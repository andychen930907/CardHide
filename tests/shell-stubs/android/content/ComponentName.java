package android.content;
public class ComponentName {
    private final String name;
    public ComponentName(String name) { this.name = name; }
    public String getPackageName() { return name; }
}
