"""Build with JDK 21+, Android SDK platform 36 and build-tools 36.0.0.
Usage: python build.py --sdk PATH --keystore PATH
Xposed API 82 is compile-only; LSPosed supplies it at runtime.
"""
import argparse
import io
import urllib.request
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True)
parser.add_argument('--keystore', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent
sdk = Path(args.sdk)
build = root / 'build'
build.mkdir(exist_ok=True)
tools = sdk / 'build-tools' / '36.0.0'
android = sdk / 'platforms' / 'android-36' / 'android.jar'
src = root / 'app' / 'src' / 'main'
api = root / 'app' / 'libs' / 'xposed-api-82.jar'
java_home = Path(shutil.which('javac')).parent
# Shizuku AARs contain code only; the provider is declared explicitly in our manifest.
runtime = []
for artifact in ('api', 'provider', 'shared', 'aidl'):
    jar = root / 'app' / 'libs' / ('shizuku-' + artifact + '.jar')
    if not jar.exists():
        url = f'https://repo.maven.apache.org/maven2/dev/rikka/shizuku/{artifact}/13.1.5/{artifact}-13.1.5.aar'
        with urllib.request.urlopen(url) as response:
            with zipfile.ZipFile(io.BytesIO(response.read())) as archive:
                jar.write_bytes(archive.read('classes.jar'))
    runtime.append(jar)

def run(*command):
    subprocess.run([str(x) for x in command], check=True, cwd=root)

run(tools / 'aapt2.exe', 'compile', '--dir', src / 'res', '-o', build / 'resources.zip')
run(tools / 'aapt2.exe', 'link', '-o', build / 'base.apk', '-I', android,
    '--manifest', src / 'AndroidManifest.xml', '-A', src / 'assets', build / 'resources.zip')
classes = build / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
run(java_home / 'javac.exe', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-classpath', os.pathsep.join(str(p) for p in [android, api] + runtime), '-d', classes,
    *sorted((src / 'java').rglob('*.java')))
run(java_home / 'jar.exe', 'cf', build / 'classes.jar', '-C', classes, '.')
run(java_home / 'java.exe', '-cp', tools / 'lib' / 'd8.jar', 'com.android.tools.r8.D8',
    '--lib', android, '--classpath', api, '--min-api', '31', '--output', build, build / 'classes.jar', *runtime)
shutil.copyfile(build / 'base.apk', build / 'unsigned.apk')
with zipfile.ZipFile(build / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
    archive.write(build / 'classes.dex', 'classes.dex')
run(tools / 'zipalign.exe', '-f', '-p', '4', build / 'unsigned.apk', build / 'aligned.apk')
password = os.environ.get('CARDHIDE_KEY_PASSWORD')
if not password:
    raise SystemExit('Set CARDHIDE_KEY_PASSWORD before signing.')
key = Path(args.keystore).resolve()
if not key.exists():
    run(java_home / 'keytool.exe', '-genkeypair', '-keystore', key, '-storepass:env',
        'CARDHIDE_KEY_PASSWORD', '-keypass:env', 'CARDHIDE_KEY_PASSWORD', '-alias', 'cardhide',
        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=CardHide Local Build')
version = ET.parse(src / 'AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
apk = build / ('CardHide-' + version + '.apk')
run(java_home / 'java.exe', '-jar', tools / 'lib' / 'apksigner.jar', 'sign', '--ks', key,
    '--ks-key-alias', 'cardhide', '--ks-pass', 'env:CARDHIDE_KEY_PASSWORD',
    '--key-pass', 'env:CARDHIDE_KEY_PASSWORD', '--out', apk, build / 'aligned.apk')
run(java_home / 'java.exe', '-jar', tools / 'lib' / 'apksigner.jar', 'verify', '--verbose', apk)
run(java_home / 'javac.exe', '-encoding', 'UTF-8', '-d', build / 'tests',
    src / 'java' / 'tw' / 'local' / 'cardhide' / 'Selection.java', root / 'tests' / 'SelectionTest.java')
run(java_home / 'java.exe', '-cp', build / 'tests', 'SelectionTest')
run(java_home / 'javac.exe', '-encoding', 'UTF-8', '-d', build / 'tests',
    src / 'java' / 'tw' / 'local' / 'cardhide' / 'CropMath.java', root / 'tests' / 'CropMathTest.java')
run(java_home / 'java.exe', '-cp', build / 'tests', 'CropMathTest')
run(java_home / 'javac.exe', '-encoding', 'UTF-8', '-d', build / 'shell-tests',
    *sorted((root / 'tests' / 'shell-stubs').rglob('*.java')),
    src / 'java' / 'tw' / 'local' / 'cardhide' / 'ShellService.java', root / 'tests' / 'ShellServiceTest.java')
run(java_home / 'java.exe', '-cp', build / 'shell-tests', 'ShellServiceTest')
print('Built:', apk)
