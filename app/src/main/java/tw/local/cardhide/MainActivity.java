package tw.local.cardhide;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 40, CROP_IMAGE = 41;
    private static final String LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS";
    private FrameLayout backgroundRoot;
    private TextView permissionButton;
    private String loadProblem;
    private int loadGeneration;
    private Bitmap glassBitmap;
    private SharedPreferences preferences, appearance;
    private final Set<String> selected = new HashSet<>();
    private final List<App> apps = new ArrayList<>(), shown = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView count, empty, summary;
    private TextView[] tabs;
    private ListView list;
    private SwipeFrame pages;
    private ShellClient shell;
    private TextView activationStatus;
    private String shellStatus = "尚未連線";
    private boolean shellMode;
    private boolean writable, enabled, dark, glass, loaded, updatingMaster;
    private int ink, muted, accent, page;
    private final String[] queries = {"", ""};
    private EditText search;
    private Bitmap wallpaper;
    private static final class App {
        final String label, name;
        final ApplicationInfo info;
        App(String label, String name, ApplicationInfo info) {
            this.label = label; this.name = name; this.info = info;
        }
    }

    @Override public void onCreate(Bundle state) {
        appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        String mode = appearance.getString("theme", "system");
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(mode) || ("system".equals(mode) && systemDark);
        glass = appearance.getBoolean("glass", true);
        setTheme(getResources().getIdentifier(dark ? "CardHideDark" : "CardHideLight", "style", getPackageName()));
        super.onCreate(state);
        if (state != null) {
            page = state.getInt("page", 0);
            queries[0] = state.getString("query0", "");
            queries[1] = state.getString("query1", "");
        }
        shellMode = "shizuku".equals(appearance.getString("activation", "lsposed"));
        try {
            preferences = getSharedPreferences(shellMode ? "shell_selection" : "selection", shellMode ? MODE_PRIVATE : MODE_WORLD_READABLE);
            writable = true;
            selected.addAll(preferences.getStringSet("packages", Collections.emptySet()));
            enabled = preferences.getBoolean("enabled", false);
        } catch (SecurityException error) { writable = false; }
        ink = Color.parseColor(dark ? "#F7F7FA" : "#19191D");
        muted = Color.parseColor(dark ? "#D0D0D8" : "#484850");
        accent = Color.parseColor(dark ? "#B8D4FF" : "#2055A5");
        wallpaper = BitmapFactory.decodeFile(new File(getFilesDir(), "wallpaper.jpg").getPath());
        shell = new ShellClient(this, status -> {
            shellStatus = status;
            if (activationStatus != null) activationStatus.setText(shellStatus);
        });
        if (shellMode) shell.configure(enabled, selected);
        render();
        if (shellMode) shell.connect(false);
        if (!appearance.getBoolean("list_intro_v3", false)) {
            appearance.edit().putBoolean("list_intro_v3", true).apply();
            getWindow().getDecorView().post(() -> showListPermission());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        reloadApps();
    }

    private boolean listPermissionDenied() {
        try {
            android.content.pm.PermissionInfo info = getPackageManager().getPermissionInfo(LIST_PERMISSION, 0);
            return "com.lbe.security.miui".equals(info.packageName)
                    && checkSelfPermission(LIST_PERMISSION) != PackageManager.PERMISSION_GRANTED;
        } catch (PackageManager.NameNotFoundException ignored) { return false; }
    }

    private void showListPermission() {
        showDialog(new AlertDialog.Builder(this).setTitle("取得應用程式清單")
                .setMessage("需要讀取已安裝應用，才能讓你選擇要隱藏的卡片。清單只在本機使用。\n\n按「前往權限設定」後，請找到「取得應用程式清單／獲取應用列表」，選擇「允許／始終允許」，再返回本應用。\n\n若系統改為開啟應用資訊頁，請進入「權限／其他權限」設定。")
                .setNegativeButton("稍後", null)
                .setNeutralButton("重新載入", (d, w) -> {
                    appearance.edit().putBoolean("list_consent", true).apply(); reloadApps();
                })
                .setPositiveButton("前往權限設定", (d, w) -> openListSettings()));
    }

    private void openListSettings() {
        appearance.edit().putBoolean("list_consent", true).apply();
        Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .addCategory(Intent.CATEGORY_DEFAULT).setPackage("com.miui.securitycenter")
                .putExtra("extra_pkgname", getPackageName()).putExtra("extra_package_uid", android.os.Process.myUid());
        try { startActivity(miui); }
        catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()))); }
            catch (android.content.ActivityNotFoundException | SecurityException missing) {
                toast("請手動開啟：設定 → 應用管理 → HyperOS後台卡片隱藏 → 權限 → 取得應用程式清單");
            }
        }
    }

    private void reloadApps() {
        final int generation = ++loadGeneration;
        if (!appearance.getBoolean("list_consent", false) || listPermissionDenied()) {
            loaded = true; apps.clear();
            for (String name : selected) apps.add(new App("已儲存的應用（待授權讀取）", name, null));
            loadProblem = "尚未取得應用清單\n請點選上方「取得應用清單」開啟權限";
            filter(); return;
        }
        loaded = false; loadProblem = null; filter();
        new Thread(() -> {
            List<App> result = new ArrayList<>();
            String problem = null;
            try {
                PackageManager pm = getPackageManager();
                for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                    if (info.packageName.equals(getPackageName())) continue;
                    String label;
                    try { label = info.loadLabel(pm).toString(); }
                    catch (RuntimeException ignored) { label = info.packageName; }
                    result.add(new App(label, info.packageName, info));
                }
                Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
                if (result.isEmpty()) problem = "系統沒有回傳可用清單\n請檢查「取得應用程式清單」權限後重新載入";
            } catch (RuntimeException error) {
                problem = "無法讀取應用清單\n請檢查權限後重新載入";
            }
            final String errorText = problem;
            runOnUiThread(() -> {
                if (isDestroyed() || generation != loadGeneration) return;
                apps.clear(); apps.addAll(result);
                for (String name : selected) {
                    boolean found = false;
                    for (App app : apps) if (app.name.equals(name)) { found = true; break; }
                    if (!found) apps.add(new App("目前無法讀取的應用", name, null));
                }
                loaded = true; loadProblem = errorText; filter();
            });
        }, "load-apps").start();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        state.putString("query0", queries[0]);
        state.putString("query1", queries[1]);
        super.onSaveInstanceState(state);
    }

    private void render() {
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarContrastEnforced(false);
        int bars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;

        FrameLayout root = new FrameLayout(this);
        backgroundRoot = root;
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (wallpaper != null) backdrop.setImageBitmap(wallpaper);
        else backdrop.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xff17171c, 0xff34333d, 0xff15151a}
                     : new int[]{0xffe9e9ef, 0xfffafafd, 0xffc8c7d0}));
        glassBitmap = wallpaper == null ? null : GlassSurface.softSample(wallpaper);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(this);
        veil.setBackgroundColor(dark ? 0x40000000 : 0x30ffffff);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = column();
        body.setPadding(dp(20), dp(12), dp(20), dp(8));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            body.setPadding(dp(20) + safe.left, dp(12) + safe.top, dp(20) + safe.right, dp(8) + safe.bottom);
            return insets;
        });
        int width = getResources().getConfiguration().screenWidthDp > 920 ? dp(900) : -1;
        root.addView(body, new FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        root.requestApplyInsets();
        root.post(() -> {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(dark ? 0 : bars, bars);
        });

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        titles.addView(label("CARD HIDE", 11, muted));
        titles.addView(label("HyperOS後台卡片隱藏", 23, ink));
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = pill("外觀  ⚙", false);
        settings.setContentDescription("外觀設定：主題、玻璃效果與背景圖片");
        settings.setOnClickListener(v -> showAppearance());
        heading.addView(settings);
        body.addView(heading);

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout workspace = new LinearLayout(this);
        workspace.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout controls = column();
        LinearLayout results = column();
        if (landscape) {
            controls.setPadding(0, 0, dp(10), 0);
            results.setPadding(dp(10), 0, 0, 0);
            ScrollView controlScroll = new ScrollView(this);
            controlScroll.setFillViewport(true);
            controlScroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
            workspace.addView(controlScroll, new LinearLayout.LayoutParams(0, -1, 42));
            workspace.addView(results, new LinearLayout.LayoutParams(0, -1, 58));
        } else {
            workspace.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            workspace.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(-1, 0, 1);
        workspaceParams.topMargin = dp(landscape ? 6 : 0);
        body.addView(workspace, workspaceParams);

        LinearLayout hero = column();
        hero.setPadding(dp(18), dp(12), dp(18), dp(12));
        hero.setBackground(surface(22));
        Switch master = new Switch(this);
        master.setText("啟用卡片隱藏");
        master.setTextSize(17);
        master.setTextColor(ink);
        master.setMinHeight(dp(48));
        tintSwitch(master);
        master.setChecked(enabled);
        master.setEnabled(true);
        hero.addView(master);
        summary = label("", 13, muted);
        hero.addView(summary);
        master.setOnCheckedChangeListener((button, checked) -> {
            if (updatingMaster) return;
            if (checked && shellMode && !shell.connected()) {
                updatingMaster = true; button.setChecked(false); updatingMaster = false;
                shell.connect(true); return;
            }
            if (checked && !writable) {
                updatingMaster = true; button.setChecked(false); updatingMaster = false;
                showRebootGuide(); return;
            }
            boolean old = enabled;
            enabled = checked;
            if (!save()) {
                enabled = old;
                updatingMaster = true; button.setChecked(old); updatingMaster = false;
            } else if (checked && !shellMode && !appearance.getBoolean("reboot_guide_seen_v7", false)) {
                showRebootGuide();
            }
            filter();
        });
        addSpace(controls, hero, landscape ? 6 : 12);
        TextView activation = pill(shellMode ? "啟動方式：Shizuku（無 Root）  ▾" : "啟動方式：Root / LSPosed  ▾", false);
        activation.setOnClickListener(v -> chooseActivation());
        addSpace(controls, activation, 8);
        if (shellMode) {
            activationStatus = label(shellStatus, 13, muted);
            addSpace(controls, activationStatus, 8);
            TextView connect = pill("連線／授權 Shizuku", false);
            connect.setOnClickListener(v -> shell.connect(true));
            addSpace(controls, connect, 8);
            TextView check = pill("檢測功能狀態", false);
            check.setOnClickListener(v -> shell.check());
            addSpace(controls, check, 8);
        } else activationStatus = null;
        permissionButton = pill("取得應用清單  ↗", false);
        permissionButton.setOnClickListener(v -> showListPermission());
        addSpace(controls, permissionButton, 8);
        if (!writable) {
            TextView warning = label("請在 LSPosed 啟用模組、勾選系統框架並重開機。", 13, muted);
            warning.setOnClickListener(v -> showHelp());
            addSpace(controls, warning, 8);
        }
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabRow.setBackground(surface(18));
        tabs = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int target = i;
            tabs[i] = pill("", page == i);
            tabs[i].setOnClickListener(v -> switchPage(target, target == 1 ? 1 : -1));
            tabRow.addView(tabs[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        addSpace(results, tabRow, landscape ? 0 : 16);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(ink);
        search.setHintTextColor(muted);
        search.setPadding(dp(16), dp(8), dp(16), dp(8));
        search.setMinHeight(dp(50));
        search.setBackground(surface(16));
        search.setText(queries[page]);
        search.setHint("搜尋名稱或套件名稱");
        addSpace(results, search, 12);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int n, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int n) {
                queries[page] = s.toString(); filter();
            }
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        count = label("", 12, muted);
        info.addView(count, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView clear = label("全部恢復", 13, accent);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(8), 0, dp(8), 0);
        clear.setMinHeight(dp(48));
        clear.setOnClickListener(v -> clearSelection());
        clear.setEnabled(writable);
        info.addView(clear);
        results.addView(info);

        pages = new SwipeFrame();
        pages.setBackground(surface(22));
        pages.setClipToOutline(true);
        list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new android.graphics.drawable.ColorDrawable(dark ? 0x18ffffff : 0x140e3449));
        list.setDividerHeight(dp(1));
        list.setPadding(dp(10), dp(6), dp(10), dp(6));
        list.setClipToPadding(false);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View recycled, ViewGroup parent) {
                App app = shown.get(p);
                LinearLayout row;
                if (recycled instanceof LinearLayout) row = (LinearLayout) recycled;
                else {
                    row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(8), dp(10), dp(8), dp(10));
                    ImageView icon = new ImageView(MainActivity.this);
                    row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    LinearLayout words = column();
                    words.setPadding(dp(12), 0, dp(8), 0);
                    TextView name = label("", 16, ink);
                    name.setMaxLines(2);
                    words.addView(name);
                    TextView pkg = label("", 11, muted);
                    pkg.setMaxLines(1);
                    pkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    words.addView(pkg);
                    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                    CheckBox check = new CheckBox(MainActivity.this);
                    check.setButtonTintList(tint());
                    check.setMinWidth(dp(48)); check.setMinHeight(dp(48));
                    row.addView(check);
                }
                ImageView icon = (ImageView) row.getChildAt(0);
                try { icon.setImageDrawable(app.info == null ? getPackageManager().getDefaultActivityIcon()
                        : app.info.loadIcon(getPackageManager())); }
                catch (RuntimeException e) { icon.setImageDrawable(getPackageManager().getDefaultActivityIcon()); }
                LinearLayout words = (LinearLayout) row.getChildAt(1);
                ((TextView) words.getChildAt(0)).setText(app.label);
                ((TextView) words.getChildAt(1)).setText(app.name);
                CheckBox check = (CheckBox) row.getChildAt(2);
                check.setOnCheckedChangeListener(null);
                check.setChecked(selected.contains(app.name));
                check.setEnabled(writable);
                check.setContentDescription((selected.contains(app.name) ? "恢復卡片：" : "隱藏卡片：") + app.label);
                check.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) selected.add(app.name); else selected.remove(app.name);
                    if (!save()) {
                        if (checked) selected.remove(app.name); else selected.add(app.name);
                    }
                    filter();
                });
                row.setOnClickListener(v -> { if (writable) check.toggle(); });
                return row;
            }
        };
        list.setAdapter(adapter);
        pages.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = label("", 16, muted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(20), dp(24), dp(20));
        pages.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        results.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = label("左右滑動切換分頁  ·  使用說明 ↗", 12, muted);
        footer.setGravity(Gravity.CENTER);
        footer.setMinHeight(dp(44));
        footer.setOnClickListener(v -> showHelp());
        results.addView(footer);
        filter();
    }

    private void switchPage(int target, int direction) {
        if (target == page) return;
        page = target;
        search.setText(queries[page]);
        search.clearFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        filter();
        list.setSelection(0);
        pages.setTranslationX(dp(22) * direction);
        pages.setAlpha(0.6f);
        pages.animate().translationX(0).alpha(1).setDuration(180).start();
    }

    private final class SwipeFrame extends FrameLayout {
        private float downX, downY;
        private boolean vertical;
        SwipeFrame() { super(MainActivity.this); }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX(); downY = e.getY(); vertical = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                if (dy > dp(12) && dy > dx) vertical = true;
                if (!vertical && dx > dp(20) && dx > dy * 1.5f) return true;
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - downX;
                if (!vertical && Math.abs(dx) > dp(48) && Math.abs(dx) > Math.abs(e.getY() - downY) * 1.5f)
                    switchPage(1 - page, dx > 0 ? -1 : 1);
                performClick();
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private boolean save() {
        if (!writable) return false;
        boolean ok = preferences.edit().putStringSet("packages", new HashSet<>(selected))
                .putBoolean("enabled", enabled).putInt("user", android.os.Process.myUid() / 100000).commit();
        if (!ok) toast("設定寫入失敗，請重新開啟應用後重試");
        if (ok && shellMode) shell.configure(enabled, selected);
        return ok;
    }

    private void filter() {
        shown.clear();
        String query = queries[page].trim().toLowerCase(Locale.ROOT);
        for (App app : apps) {
            if ((page == 1) != selected.contains(app.name)) continue;
            if (app.info == null && page == 0) continue;
            if (app.label.toLowerCase(Locale.ROOT).contains(query) || app.name.toLowerCase(Locale.ROOT).contains(query)) shown.add(app);
        }
        if (count == null) return;
        summary.setText((enabled ? (shellMode ? "已要求啟用（狀態見下方）" : "已啟用") : "已暫停") + "  ·  " + selected.size() + " 個應用在隱藏名單中");
        count.setText((page == 0 ? "勾選後移到「已隱藏」" : "取消勾選即可恢復卡片") + "  ·  " + shown.size() + " 個");
        tabs[0].setText("01  應用清單");
        tabs[1].setText("02  已隱藏  ·  " + selected.size());
        for (int i = 0; i < 2; i++) {
            tabs[i].setBackground(i == page ? surface(14) : rounded(Color.TRANSPARENT, 14));
            tabs[i].setTextColor(i == page ? accent : muted);
            tabs[i].setSelected(i == page);
        }
        permissionButton.setText(loadProblem != null || !appearance.getBoolean("list_consent", false)
                ? "取得應用清單  ↗" : "應用清單權限 · 重新載入  ↗");
        empty.setText(page == 0 && loadProblem != null ? loadProblem : !loaded ? "正在整理應用清單…" : !query.isEmpty() ? "找不到符合的應用\n試試其他名稱或套件名稱"
                : page == 1 ? "這裡還沒有隱藏的應用\n到第一頁勾選，就會出現在這裡" : "所有可用應用都已加入隱藏名單");
        adapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        if (!writable || selected.isEmpty()) return;
        showDialog(new AlertDialog.Builder(this).setTitle("恢復全部卡片？")
                .setMessage("清空隱藏名單，不會關閉任何應用。")
                .setNegativeButton("返回", null).setPositiveButton("全部恢復", (d, w) -> {
                    Set<String> old = new HashSet<>(selected); selected.clear();
                    if (!save()) selected.addAll(old);
                    filter();
                }));
    }

    private void showAppearance() {
        LinearLayout content = column();
        content.setPadding(dp(24), dp(8), dp(24), dp(16));
        content.addView(label("主題", 13, muted));
        RadioGroup modes = new RadioGroup(this);
        String[] names = {"自動偵測 · 跟隨系統", "淺色模式", "深色模式"};
        String[] values = {"system", "light", "dark"};
        for (int i = 0; i < names.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(100 + i); radio.setText(names[i]); radio.setTextColor(ink);
            radio.setButtonTintList(tint()); radio.setMinHeight(dp(48));
            modes.addView(radio);
            if (values[i].equals(appearance.getString("theme", "system"))) modes.check(radio.getId());
        }
        content.addView(modes);
        Switch glassSwitch = new Switch(this);
        glassSwitch.setText("玻璃材質"); glassSwitch.setTextColor(ink);
        glassSwitch.setMinHeight(dp(56)); glassSwitch.setChecked(glass); tintSwitch(glassSwitch);
        content.addView(glassSwitch);
        content.addView(label("透明材質、局部折射與高光邊緣。\n關閉後取消折射，圖片仍穿透所有區塊。", 12, muted));
        TextView choose = pill("選擇圖片並裁切", true);
        addSpace(content, choose, 20);
        TextView remove = pill("恢復預設背景", false);
        addSpace(content, remove, 8);
        content.addView(label("選圖後可拖曳、雙指縮放及調整裁切框。", 12, muted));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("外觀設定").setView(scroll)
                .setPositiveButton("完成", null).create();
        modes.setOnCheckedChangeListener((group, id) -> {
            if (appearance.edit().putString("theme", values[id - 100]).commit()) {
                dialog.dismiss(); recreate();
            } else toast("外觀設定儲存失敗");
        });
        glassSwitch.setOnCheckedChangeListener((button, checked) -> {
            appearance.edit().putBoolean("glass", checked).apply(); glass = checked; render();
        });
        choose.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/*");
            try { startActivityForResult(intent, PICK_IMAGE); }
            catch (android.content.ActivityNotFoundException e) { toast("找不到圖片選擇器，請啟用系統檔案應用"); }
        });
        remove.setOnClickListener(v -> {
            File file = new File(getFilesDir(), "wallpaper.jpg");
            if (file.exists() && !file.delete()) { toast("無法移除背景圖片，請稍後重試"); return; }
            wallpaper = null; dialog.dismiss(); render();
        });
        dialog.show();
        skinDialog(dialog);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK) return;
        if (request == CROP_IMAGE) {
            wallpaper = BitmapFactory.decodeFile(new File(getFilesDir(), "wallpaper.jpg").getPath());
            render(); toast("背景圖片已更新");
        } else if (request == PICK_IMAGE && data != null && data.getData() != null) {
            Intent crop = new Intent(this, CropActivity.class).setData(data.getData())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(crop, CROP_IMAGE);
        }
    }

    @Override protected void onDestroy() {
        if (shell != null) shell.close();
        super.onDestroy();
    }

    private void chooseActivation() {
        if (enabled) { toast("請先關閉卡片隱藏開關，再切換啟動方式"); return; }
        showDialog(new AlertDialog.Builder(this).setTitle("啟動方式")
                .setSingleChoiceItems(new String[]{"Root / LSPosed（已驗證）", "Shizuku 無 Root（實驗性，需 ROM 支援）"}, shellMode ? 1 : 0,
                (dialog, which) -> {
                    appearance.edit().putString("activation", which == 1 ? "shizuku" : "lsposed").apply();
                    dialog.dismiss(); recreate();
                }).setNegativeButton("取消", null));
    }

    private void showHelp() {
        showDialog(new AlertDialog.Builder(this).setTitle("使用說明 · 1.0")
                .setMessage("第一頁勾選應用後，會移到第二頁「已隱藏」。左右滑動或點分頁切換；第二頁取消勾選即可恢復卡片。\n\n在 LSPosed 啟用模組、作用域選「系統框架／android」並手動重開機。設定變更後，等約 1 秒再進入最近任務。\n\nShizuku 請先用無線偵錯啟動並授權，再開啟開關。選好應用並開啟其中一個後，可點「檢測功能狀態」；檢測會短暫恢復再重新隱藏，用系統最近任務清單確認功能是否真的生效。若顯示「系統層正常」但澎湃桌面仍顯示卡片，代表桌面使用快取或廠商私有清單，此 Shizuku 路徑無法保證隱藏，請改用 Root / LSPosed。\n\n停用開關會恢復卡片，卸載前請先停用。兩種方式都不關閉應用、不保證永久保活。使用者已回報 LSPosed 隱藏核心在小米 Pad 8 Pro／OS 4.0.0.44／KernelSU 32680-5／LSPosed 2.21 正常生效。\n\n外觀設定只影響本應用。跟隨系統模式會隨系統深淺色切換。選擇的圖片縮放後保存在本機，不需要相簿讀取權限。")
                .setPositiveButton("知道了", null));
    }

    private void showDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create(); dialog.show(); skinDialog(dialog);
    }

    private void skinDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            if (wallpaper != null && glass) {
                GlassSurface material = new GlassSurface(backgroundRoot, wallpaper, glassBitmap, dp(26), dark, true);
                material.setAnchor(dialog.getWindow().getDecorView());
                dialog.getWindow().setBackgroundDrawable(material);
            } else {
                // A modal must occlude underlying controls; translucency alone does not do this.
                dialog.getWindow().setBackgroundDrawable(rounded(dark ? 0xff24242a : 0xfff6f6fa, 26));
            }
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.55f);
        }
    }

    private void showRebootGuide() {
        appearance.edit().putBoolean("reboot_guide_seen_v7", true).apply();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("請手動重新啟動裝置")
                .setMessage("首次啟用 Root / LSPosed 模式後，必須手動重新啟動裝置，系統框架才會載入隱藏功能。\n\n請確認 LSPosed 已啟用「HyperOS後台卡片隱藏」，作用域勾選「系統框架／android」，再使用裝置電源選單重新啟動。\n\n此提示無法關閉；裝置重新啟動後會自動消失。")
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        skinDialog(dialog);
    }

    private ColorStateList tint() {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{accent, muted});
    }
    private void tintSwitch(Switch toggle) {
        toggle.setThumbTintList(tint());
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{dark ? 0xff68687a : 0xffa9b9d2, dark ? 0xff474750 : 0xffb9b9c2}));
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private android.graphics.drawable.Drawable surface(int radius) {
        return new GlassSurface(backgroundRoot, wallpaper, glassBitmap, dp(radius), dark, glass);
    }
    private TextView pill(String text, boolean active) {
        TextView view = label(text, 14, active ? accent : ink);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(48));
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        view.setBackground(surface(14));
        view.setClickable(true); view.setFocusable(true); return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (wallpaper != null) view.setShadowLayer(dp(2), 0, dp(1), dark ? 0xd9000000 : 0xd9ffffff);
        return view;
    }
    private void addSpace(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); parent.addView(child, params);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
