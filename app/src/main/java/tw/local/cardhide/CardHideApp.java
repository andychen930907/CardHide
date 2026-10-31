package tw.local.cardhide;

import android.app.Application;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.Collections;
import java.util.HashSet;
import rikka.shizuku.Shizuku;

/** Restores the saved Shizuku configuration when its wireless-debugging service returns. */
public final class CardHideApp extends Application {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Shizuku.UserServiceArgs args;
    private boolean bound;
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener dead = () -> bound = false;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            applySaved(service);
        }
        public void onServiceDisconnected(ComponentName name) { bound = false; }
    };

    @Override public void onCreate() {
        super.onCreate();
        args = new Shizuku.UserServiceArgs(new ComponentName(this, ShellService.class))
                .daemon(true).processNameSuffix("cardhide_shell").version(7);
        Shizuku.addBinderReceivedListenerSticky(received, main);
        Shizuku.addBinderDeadListener(dead, main);
    }

    private void connect() {
        if (!"shizuku".equals(getSharedPreferences("appearance", MODE_PRIVATE)
                .getString("activation", "lsposed"))) return;
        SharedPreferences saved = getSharedPreferences("shell_selection", MODE_PRIVATE);
        if (!saved.getBoolean("enabled", false) || bound) return;
        try {
            if (!Shizuku.pingBinder() || Shizuku.getUid() != 2000
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
            Shizuku.bindUserService(args, connection);
            bound = true;
        } catch (RuntimeException ignored) { bound = false; }
    }

    private void applySaved(IBinder service) {
        SharedPreferences saved = getSharedPreferences("shell_selection", MODE_PRIVATE);
        boolean enabled = saved.getBoolean("enabled", false);
        int user = saved.getInt("user", android.os.Process.myUid() / 100000);
        String[] packages = new HashSet<>(saved.getStringSet("packages", Collections.emptySet()))
                .toArray(new String[0]);
        new Thread(() -> {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(ShellService.DESCRIPTOR);
                data.writeInt(enabled ? 1 : 0); data.writeInt(user); data.writeStringArray(packages);
                service.transact(ShellService.CONFIGURE, data, reply, 0);
                reply.readException();
            } catch (Exception ignored) { bound = false; }
            finally { data.recycle(); reply.recycle(); }
        }, "restore-shizuku-settings").start();
    }
}
