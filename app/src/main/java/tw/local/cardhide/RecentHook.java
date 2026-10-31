package tw.local.cardhide;

import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class RecentHook implements IXposedHookLoadPackage {
    private XSharedPreferences preferences;
    private Set<String> selected = Collections.emptySet();
    private boolean enabled;
    private int configuredUser = -1;
    private long lastRead = -2000;
    private boolean loggedError;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!"android".equals(load.packageName)) return;
        try {
            Class<?> service = XposedHelpers.findClass(
                    "com.android.server.wm.ActivityTaskManagerService", load.classLoader);
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(service, "getRecentTasks", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || param.getResult() == null) return;
                    try {
                        Object filtered = filter(param.getResult());
                        if (filtered != param.getResult()) param.setResult(filtered);
                    } catch (Throwable error) {
                        // A ROM mismatch leaves the original presentation result untouched.
                        if (!loggedError) {
                            loggedError = true;
                            XposedBridge.log("CardHide: filtering failed; returning original list");
                            XposedBridge.log(error);
                        }
                    }
                }
            });
            XposedBridge.log("CardHide: getRecentTasks hooks=" + hooks.size());
        } catch (Throwable error) {
            XposedBridge.log("CardHide: unsupported framework");
            XposedBridge.log(error);
        }
    }

    private synchronized Object filter(Object result) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRead >= 1000) {
            if (preferences == null) preferences = new XSharedPreferences("tw.local.cardhide", "selection");
            preferences.reload();
            selected = new HashSet<>(preferences.getStringSet("packages", Collections.emptySet()));
            enabled = preferences.getBoolean("enabled", false);
            configuredUser = preferences.getInt("user", -1);
            lastRead = now;
        }
        if (!enabled || selected.isEmpty() || configuredUser < 0) return result;
        boolean isList = result instanceof List;
        List<?> source = isList ? (List<?>) result : (List<?>) XposedHelpers.callMethod(result, "getList");
        ArrayList<Object> visible = new ArrayList<>();
        for (Object info : source) {
            Intent intent = (Intent) XposedHelpers.getObjectField(info, "baseIntent");
            ComponentName base = intent == null ? null : intent.getComponent();
            ComponentName real = (ComponentName) XposedHelpers.getObjectField(info, "realActivity");
            int user = XposedHelpers.getIntField(info, "userId");
            if (!Selection.hide(enabled, selected, base == null ? null : base.getPackageName(),
                    real == null ? null : real.getPackageName(), user, configuredUser)) visible.add(info);
        }
        if (visible.size() == source.size()) return result;
        // Only copy the result of the Binder query. Never mutate Task, RecentTasks, or lifecycle state.
        return isList ? visible : XposedHelpers.newInstance(result.getClass(), visible);
    }
}
