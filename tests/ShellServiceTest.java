import tw.local.cardhide.ShellService;
import android.content.Intent;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Host-side service contract tests; does not establish real ROM compatibility. */
public class ShellServiceTest {
    public static class Task {
        public int taskId, userId;
        public Intent baseIntent;
        public Object token = new Object();
        Task(int id, int user, String pkg) { taskId = id; userId = user; baseIntent = new Intent(pkg); }
    }
    public static class Manager {
        public List<Task> tasks = new ArrayList<>();
        public List<Task> recents = new ArrayList<>();
        public List<Task> getTasks(int max, boolean visible, boolean extra, int display) {
            if (visible) throw new AssertionError("Must include hidden tasks for restoration");
            return tasks;
        }
        public Slice getRecentTasks(int max, int flags, int user) { return new Slice(recents); }
    }
    public static class Slice {
        private final List<Task> tasks;
        Slice(List<Task> tasks) { this.tasks = tasks; }
        public List<Task> getList() { return tasks; }
    }
    public static class Transaction {
        Object token; boolean excluded;
        public void exclude(Object token, boolean value) { this.token = token; excluded = value; }
    }
    public static class Organizer {
        final Map<Object, Boolean> applied = new HashMap<>();
        final Manager manager;
        boolean reject, ineffective;
        Organizer(Manager manager) { this.manager = manager; }
        public void apply(Transaction tx) {
            if (reject) throw new SecurityException("denied");
            applied.put(tx.token, tx.excluded);
            if (!ineffective) for (Task task : manager.tasks) if (task.token == tx.token) {
                if (tx.excluded) manager.recents.remove(task);
                else if (!manager.recents.contains(task)) manager.recents.add(task);
            }
        }
    }
    static void set(Object o, String name, Object value) throws Exception {
        Field f = ShellService.class.getDeclaredField(name); f.setAccessible(true); f.set(o, value);
    }
    static Object get(Object o, String name) throws Exception {
        Field f = ShellService.class.getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        ShellService service = new ShellService();
        ((ScheduledExecutorService) get(service, "worker")).shutdownNow();
        Manager manager = new Manager(); Organizer organizer = new Organizer(manager);
        set(service, "atm", manager); set(service, "organizer", organizer);
        set(service, "transaction", Transaction.class);
        set(service, "getTasks", Manager.class.getMethod("getTasks", int.class, boolean.class, boolean.class, int.class));
        set(service, "getRecentTasks", Manager.class.getMethod("getRecentTasks", int.class, int.class, int.class));
        set(service, "exclude", Transaction.class.getMethod("exclude", Object.class, boolean.class));
        set(service, "apply", Organizer.class.getMethod("apply", Transaction.class));
        set(service, "packages", new HashSet<>(Arrays.asList("chosen"))); set(service, "enabled", true);
        Method reconcile = ShellService.class.getDeclaredMethod("reconcile"); reconcile.setAccessible(true);
        Method activeVerify = ShellService.class.getDeclaredMethod("activeVerify"); activeVerify.setAccessible(true);
        Task target = new Task(1, 0, "chosen"), other = new Task(2, 0, "other"), otherUser = new Task(3, 10, "chosen");
        manager.tasks.addAll(Arrays.asList(target, other, otherUser, new Task(4, 0, null)));
        reconcile.invoke(service);
        check(organizer.applied.size() == 1 && organizer.applied.get(target.token));
        check(((String) activeVerify.invoke(service)).startsWith("系統層正常："));
        organizer.ineffective = true; manager.recents.add(target);
        check(((String) activeVerify.invoke(service)).startsWith("異常："));
        organizer.ineffective = false; manager.recents.clear();
        Task newTask = new Task(5, 0, "chosen"); manager.tasks.add(newTask);
        reconcile.invoke(service); check(organizer.applied.get(newTask.token));
        set(service, "packages", new HashSet<String>()); reconcile.invoke(service);
        check(!organizer.applied.get(target.token) && !organizer.applied.get(newTask.token));
        set(service, "packages", new HashSet<>(Arrays.asList("chosen"))); reconcile.invoke(service);
        set(service, "enabled", false); reconcile.invoke(service);
        check(!organizer.applied.get(target.token));
        set(service, "enabled", true); organizer.reject = true;
        try { reconcile.invoke(service); throw new AssertionError("denial must propagate"); }
        catch (InvocationTargetException expected) { check(((Map<?, ?>) get(service, "changed")).isEmpty()); }
        organizer.reject = false; reconcile.invoke(service);
        manager.tasks.clear(); reconcile.invoke(service);
        check(((Map<?, ?>) get(service, "changed")).isEmpty());
        set(service, "atm", null);
        try { reconcile.invoke(service); throw new AssertionError("missing API must fail"); }
        catch (InvocationTargetException expected) { check(expected.getCause() instanceof ClassNotFoundException); }
        System.out.println("PASS: shell selection, functional status, user isolation, new tasks, deselection, disable, denial, removed tasks and missing API");
    }
}
