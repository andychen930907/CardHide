package tw.local.cardhide;

import java.util.Set;

public final class Selection {
    private Selection() {}

    public static boolean hide(boolean enabled, Set<String> selected, String basePackage,
                               String realPackage, int taskUser, int configuredUser) {
        if (!enabled || taskUser != configuredUser) return false;
        String owner = basePackage != null ? basePackage : realPackage;
        return owner != null && selected.contains(owner);
    }
}
