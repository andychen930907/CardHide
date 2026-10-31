import tw.local.cardhide.Selection;
import java.util.Collections;
import java.util.Set;

public final class SelectionTest {
    public static void main(String[] args) {
        Set<String> selected = Collections.singleton("example.music");
        check(Selection.hide(true, selected, "example.music", null, 0, 0), "selected task hidden");
        check(!Selection.hide(false, selected, "example.music", null, 0, 0), "master disabled");
        check(!Selection.hide(true, selected, "example.browser", null, 0, 0), "unselected task retained");
        check(!Selection.hide(true, selected, "example.music", null, 999, 0), "clone profile retained");
        check(Selection.hide(true, selected, null, "example.music", 0, 0), "real activity fallback");
        check(!Selection.hide(true, selected, "example.browser", "example.music", 0, 0), "base owner takes precedence");
        check(!Selection.hide(true, selected, null, null, 0, 0), "unknown owner retained");
        check(!Selection.hide(true, Collections.emptySet(), "example.music", null, 0, 0), "cleared selection restored");
        System.out.println("PASS: 8 selection checks");
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
