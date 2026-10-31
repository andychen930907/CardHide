package tw.local.cardhide;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

final class ShellClient {
    interface Listener { void changed(String status); }
    private final Activity activity;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private IBinder remote;
    private boolean closed, bound;
    private volatile boolean desiredEnabled;
    private volatile String[] desiredPackages = new String[0];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        public void run() { if (!closed) { query(false, false, new String[0]); handler.postDelayed(this, 2000); } }
    };
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> {
        if (code == 71) { if (result == PackageManager.PERMISSION_GRANTED) connect(false); else report("Shizuku 授權被拒絕"); }
    };
    private final Shizuku.OnBinderReceivedListener binderReceived = () -> {
        if (!closed) connect(false);
    };
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        remote = null; bound = false;
        handler.removeCallbacks(poll);
        report("Shizuku 已重新啟動，正在等待服務恢復…");
    };
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = binder;
            report("Shizuku 已重新連線，正在恢復隱藏設定…");
            query(true, desiredEnabled, desiredPackages.clone());
            handler.removeCallbacks(poll); handler.postDelayed(poll, 2000);
        }
        public void onServiceDisconnected(ComponentName name) {
            remote = null; bound = false; handler.removeCallbacks(poll);
            report("Shizuku 連線中斷；服務恢復後會自動重新套用");
        }
    };
    ShellClient(Activity activity, Listener listener) {
        this.activity = activity; this.listener = listener;
        args = new Shizuku.UserServiceArgs(new ComponentName(activity, ShellService.class))
                .daemon(true).processNameSuffix("cardhide_shell").version(6);
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderReceivedListenerSticky(binderReceived, handler);
        Shizuku.addBinderDeadListener(binderDead, handler);
    }
    void connect(boolean request) {
        try {
            if (!Shizuku.pingBinder()) { report("請先在 Shizuku 透過無線偵錯啟動服務"); return; }
            if (Shizuku.getUid() != 2000) { report("請使用 Shizuku 的無線偵錯模式（shell），Root 請選 LSPosed"); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (request) Shizuku.requestPermission(71);
                else report("請點選連線按鈕授權 Shizuku");
                return;
            }
            if (!bound) { Shizuku.bindUserService(args, connection); bound = true; }
            report("正在連線…");
        } catch (RuntimeException e) { report("Shizuku 連線失敗：" + e.getMessage()); }
    }
    boolean connected() { return remote != null && remote.isBinderAlive(); }
    void configure(boolean enabled, Set<String> packages) {
        desiredEnabled = enabled;
        desiredPackages = new HashSet<>(packages).toArray(new String[0]);
        query(true, desiredEnabled, desiredPackages.clone());
    }
    private void query(boolean config, boolean enabled, String[] packages) {
        IBinder binder = remote;
        if (binder == null) { if (config) report("尚未連線 Shizuku，設定尚未套用"); return; }
        worker.execute(() -> {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(ShellService.DESCRIPTOR);
                if (config) { data.writeInt(enabled ? 1 : 0); data.writeInt(android.os.Process.myUid() / 100000); data.writeStringArray(packages); }
                if (!binder.transact(config ? ShellService.CONFIGURE : ShellService.STATUS, data, reply, 0))
                    throw new IllegalStateException("服務版本不相容");
                reply.readException(); reply.readInt(); report(reply.readString());
            } catch (Exception e) { report("Shizuku 操作失敗：" + e.getMessage()); }
            finally { data.recycle(); reply.recycle(); }
        });
    }
    private void report(String status) { activity.runOnUiThread(() -> { if (!closed) listener.changed(status); }); }
    void close() {
        closed = true; handler.removeCallbacks(poll);
        Shizuku.removeRequestPermissionResultListener(permission);
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        if (bound) try { Shizuku.unbindUserService(args, connection, false); } catch (RuntimeException ignored) { }
        worker.shutdown();
    }
}
