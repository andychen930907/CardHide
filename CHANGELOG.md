# Changelog / 更新紀錄

## 1.0 build 6

- Make the Root reboot command compatible with KernelSU paths and Android power-control commands, with actionable failure status.
- Automatically reconnect and restore saved selections after the Shizuku service returns.
- Use a two-column landscape layout with independently scrollable controls and a full-height app list.

- 改善 KernelSU Root 重啟命令與失敗提示。
- Shizuku 服務恢復後自動重連並重新套用已儲存名單。
- 橫屏改為雙欄版面，控制區可獨立捲動，應用清單保留完整高度。

## 1.0 build 5

- Rename to HyperOS後台卡片隱藏 / 更新應用名稱。
- Fix light/dark dialog opacity / 修正深淺色對話框穿透。
- Add first-enable LSPosed reboot guidance and confirmation / 首次啟用重開機提醒。
- Experimental non-root Shizuku backend with ROM capability checks and task restoration. Physical-device validation pending / 實驗性 Shizuku 無 Root 模式，含相容性檢查與恢復，尚待實機驗證。

## 1.0

- Publish the existing feature set as version 1.0 (Android versionCode 4).
- Add Traditional Chinese and English documentation.
- Retain the existing signing certificate for in-place upgrades.
- No change to the task-hiding hook; this release does not imply additional device validation.

- 版本調整為 1.0（Android versionCode 4）。
- 新增繁體中文與英文說明。
- 沿用簽章，可直接覆蓋舊版。
- 隱藏核心未改動；版本更名不代表已增加實機驗證。

## 0.3.0

- Add HyperOS installed-app-list permission declaration and settings guidance.
- Refresh the app list when returning to the app.
- Replace the green palette with neutral glass-inspired surfaces.
- Add full-window wallpaper and interactive crop, zoom and pan controls.

- 補上澎湃 OS 應用清單權限宣告、設定跳轉及返回後重新載入。
- 改為中性色玻璃介面與全視窗背景。
- 加入拖曳、縮放、螢幕比例與自由比例裁切。

## 0.2.0

- Add light, dark and system themes, two-page app management, glass toggle and wallpaper selection.
- 加入三種主題、雙頁應用管理、玻璃開關及自選背景。

## 0.1.0

- Initial LSPosed module: filter selected apps from recent-task query results without removing tasks.
- 初版 LSPosed 模組：從最近任務查詢結果篩選所選應用，不移除實際任務。
