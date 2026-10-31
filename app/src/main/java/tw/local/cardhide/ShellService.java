package tw.local.cardhide;

import android.content.ComponentName;
import android.content.Intent;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs under Shizuku's shell identity; never removes or stops a task. */
public final class ShellService extends Binder {
    static final String DESCRIPTOR = "tw.local.cardhide.ShellService";
    static final int CONFIGURE = 1, STATUS = 2, CHECK = 3;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer, Object> changed = new HashMap<>();
    private Set<String> packages = new HashSet<>();
    private int user;
    private boolean enabled;
    private String status = "尚未檢查";
    private Object atm, organizer;
    private Class<?> transaction;
    private Method getTasks, getRecentTasks, exclude, apply;

    public ShellService() {
        worker.scheduleWithFixedDelay(() -> {
            synchronized (this) {
                if (enabled || !changed.isEmpty()) {
                    try { reconcile(); }
                    catch (Throwable e) { status = failure(e); enabled = false; }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void prepare() throws Exception {
        if (atm != null) return;
        Class<?> tx = Class.forName("android.window.WindowContainerTransaction");
        Method ex = tx.getMethod("setTaskForceExcludedFromRecents",
                Class.forName("android.window.WindowContainerToken"), boolean.class);
        Class<?> wo = Class.forName("android.window.WindowOrganizer");
        Object org = wo.getConstructor().newInstance();
        Method ap = wo.getMethod("applyTransaction", tx);
        // The empty transaction checks shell permission without changing a task.
        ap.invoke(org, tx.getConstructor().newInstance());
        Object service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Method tasks = Class.forName("android.app.IActivityTaskManager").getMethod("getTasks",
                int.class, boolean.class, boolean.class, int.class);
        Method recents = Class.forName("android.app.IActivityTaskManager").getMethod("getRecentTasks",
                int.class, int.class, int.class);
        tasks.invoke(service, 1000, false, false, -1);
        transaction = tx; exclude = ex; organizer = org; apply = ap;
        getTasks = tasks; getRecentTasks = recents; atm = service;
    }

    private void reconcile() throws Exception {
        prepare();
        List<?> tasks = (List<?>) getTasks.invoke(atm, 1000, false, false, -1);
        Set<Integer> alive = new HashSet<>();
        int selectedRunning = 0;
        for (Object task : tasks) {
            Class<?> type = task.getClass();
            int id = type.getField("taskId").getInt(task);
            alive.add(id);
            int taskUser = type.getField("userId").getInt(task);
            Intent intent = (Intent) type.getField("baseIntent").get(task);
            ComponentName component = intent == null ? null : intent.getComponent();
            boolean hide = enabled && taskUser == user && component != null
                    && packages.contains(component.getPackageName());
            if (hide) selectedRunning++;
            Object token = type.getField("token").get(task);
            if (hide && !changed.containsKey(id)) {
                if (token == null) throw new IllegalStateException("系統未提供任務控制權杖");
                setExcluded(token, true);
                changed.put(id, token);
            } else if (!hide && changed.containsKey(id)) {
                setExcluded(changed.get(id), false);
                changed.remove(id);
            }
        }
        changed.keySet().retainAll(alive);
        if (!enabled) status = "已暫停，卡片已恢復";
        else if (packages.isEmpty()) status = "待檢測：請先選擇至少一個應用";
        else if (selectedRunning == 0) status = "待檢測：請先開啟一個已選應用";
        else status = "已套用 " + changed.size() + " 個任務 · 請點「檢測功能狀態」驗證";
    }

    private String activeVerify() throws Exception {
        if (packages.isEmpty()) return "待檢測：請先選擇至少一個應用";
        if (changed.isEmpty()) return "待檢測：請先開啟一個已選應用，再回來檢測";
        for (Object token : changed.values()) setExcluded(token, false);
        int restored;
        try { restored = countSelectedRecents(); }
        finally { for (Object token : changed.values()) setExcluded(token, true); }
        int hidden = countSelectedRecents();
        if (restored == 0) return "無法判定：暫時恢復後，系統最近任務仍未回傳已選應用";
        if (hidden == 0) return "系統層正常：恢復與隱藏往返測試成功，共驗證 " + restored + " 個任務";
        return "異常：重新隱藏後，系統仍回傳 " + hidden
                + " 個已選任務；此 ROM 的 Shizuku 路徑未生效，請改用 Root / LSPosed";
    }

    private int countSelectedRecents() throws Exception {
        Object slice = getRecentTasks.invoke(atm, 1000, 0, user);
        List<?> recents = (List<?>) slice.getClass().getMethod("getList").invoke(slice);
        int visible = 0;
        for (Object task : recents) {
            Class<?> type = task.getClass();
            int taskUser = type.getField("userId").getInt(task);
            Intent intent = (Intent) type.getField("baseIntent").get(task);
            ComponentName component = intent == null ? null : intent.getComponent();
            if (taskUser == user && component != null && packages.contains(component.getPackageName())) visible++;
        }
        return visible;
    }

    private void setExcluded(Object token, boolean value) throws Exception {
        Object tx = transaction.getConstructor().newInstance();
        exclude.invoke(tx, token, value);
        apply.invoke(organizer, tx);
    }

    private static String failure(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        if (error instanceof NoSuchMethodException || error instanceof ClassNotFoundException)
            return "不支援：此 ROM 缺少安全隱藏任務介面（需要 Android 17 對應介面）";
        return "無法套用：" + error.getClass().getSimpleName() + " · " + String.valueOf(error.getMessage());
    }

    @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        if (code == 16777115) {
            enabled = false;
            try { reconcile(); } catch (Exception ignored) { }
            worker.shutdownNow(); System.exit(0); return true;
        }
        if (code != CONFIGURE && code != STATUS && code != CHECK) return false;
        data.enforceInterface(DESCRIPTOR);
        if (code == CONFIGURE) {
            enabled = data.readInt() != 0;
            user = data.readInt();
            packages = new HashSet<>(Arrays.asList(data.createStringArray()));
            try { reconcile(); }
            catch (Throwable e) { status = failure(e); enabled = false; }
        } else if (code == CHECK) {
            try {
                reconcile();
                if (enabled) status = activeVerify();
            } catch (Throwable e) { status = failure(e); }
        }
        reply.writeNoException();
        reply.writeInt(enabled ? 1 : 0);
        reply.writeString(status);
        return true;
    }
}
