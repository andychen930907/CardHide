package android.content;
public class Intent {
    private final ComponentName component;
    public Intent(String pkg) { component = pkg == null ? null : new ComponentName(pkg); }
    public ComponentName getComponent() { return component; }
}
