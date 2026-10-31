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
    static final int CONFIGURE = 1, STATUS = 2;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer, Object> changed = new HashMap<>();
    private Set<String> packages = new HashSet<>();
    private int user;
    private boolean enabled;
    private String status = "尚未檢查";
    private Object atm, organizer;
    private Class<?> transaction;
    private Method getTasks, exclude, apply;

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
        tasks.invoke(service, 1000, false, false, -1);
        transaction = tx; exclude = ex; organizer = org; apply = ap; getTasks = tasks; atm = service;
    }

    private void reconcile() throws Exception {
        prepare();
        List<?> tasks = (List<?>) getTasks.invoke(atm, 1000, false, false, -1);
        Set<Integer> alive = new HashSet<>();
        for (Object task : tasks) {
            Class<?> type = task.getClass();
            int id = type.getField("taskId").getInt(task);
            alive.add(id);
            int taskUser = type.getField("userId").getInt(task);
            Intent intent = (Intent) type.getField("baseIntent").get(task);
            ComponentName component = intent == null ? null : intent.getComponent();
            boolean hide = enabled && taskUser == user && component != null
                    && packages.contains(component.getPackageName());
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
        status = enabled ? "執行中 · 已套用 " + changed.size() + " 個任務（請在最近任務確認）" : "已暫停，卡片已恢復";
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
        if (code != CONFIGURE && code != STATUS) return false;
        data.enforceInterface(DESCRIPTOR);
        if (code == CONFIGURE) {
            enabled = data.readInt() != 0;
            user = data.readInt();
            packages = new HashSet<>(Arrays.asList(data.createStringArray()));
            try { reconcile(); }
            catch (Throwable e) { status = failure(e); enabled = false; }
        }
        reply.writeNoException();
        reply.writeInt(enabled ? 1 : 0);
        reply.writeString(status);
        return true;
    }
}
