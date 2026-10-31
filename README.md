# HyperOS後台卡片隱藏 (CardHide)

**繁體中文** | [English](README.en.md)

適用於 LSPosed 的 Android 模組：隱藏自行選取的應用在「最近任務」中的卡片，不主動移除任務或關閉應用。

**目前版本：1.0 build 6** · 套件名稱：`tw.local.cardhide` · Android versionCode：`6`

build 6 修正 KernelSU 自動重啟、Shizuku 服務恢復後自動重連及重新套用設定，並加入橫屏雙欄版面。裝置重開機後仍須先以無線偵錯啟動 Shizuku；Shizuku 可用後會自動讀取已啟用的設定。

## build 5：啟動方式與相容性

修正預設深／淺色對話框穿透，加入不透明底色和遮罩。首次啟用 LSPosed 會提供重開機提醒及按鈕，經再次確認才請求 Root 重新啟動。

**Root / LSPosed** 保留已實機驗證的隱藏方式。**Shizuku 無 Root（實驗性）**：先使用無線偵錯啟動 Shizuku，再於本應用選擇 Shizuku 啟動方式、連線授權並開啟隱藏。此模式不需要為啟用功能而重開機。

Shizuku 需要 ROM 提供 AOSP Android 17 的 `WindowContainerTransaction.setTaskForceExcludedFromRecents` 及 shell 的 `MANAGE_ACTIVITY_TASKS` 權限。HyperOS 版本號本身不能保證相容，應用會檢查實際介面與權限。不支援時顯示原因，不會改成移除任務或強制停止。**此模式尚未實機驗證。**

服務約每秒檢查執行中的任務，新卡片可能短暫出現；關閉設定畫面不會主動停止服務。重開機後須重新啟動 Shizuku、連線並重新套用開關；已開啟時請先關閉再開啟。「已套用任務數」代表系統呼叫成功，仍須確認最近任務的實際畫面。

切換方式或卸載前，請先關閉隱藏開關並等待「卡片已恢復」。若服務被終止，已套用標記可能持續到任務重建或裝置重開機。系統未提供讀取原本強制排除狀態的介面，請勿與其他任務排除工具同時使用此實驗模式。

## 功能

- 選擇要隱藏卡片的應用，支援搜尋名稱或套件名稱。
- 兩頁管理：第一頁顯示未選應用，第二頁管理已選名單；支援滑動及點選切換。
- 總開關、個別恢復與全部恢復。
- 澎湃 OS「取得應用程式清單」權限提示、設定跳轉與返回後重新載入。
- 淺色、深色、自動跟隨系統三種主題。
- 可開關的玻璃風格介面：透明面板、局部折射、高光邊緣。
- 全視窗自選背景，支援拖曳構圖、雙指縮放、螢幕比例和自由比例裁切。
- 設定與圖片僅儲存於本機，沒有網路權限。

> 玻璃外觀是 Android 自行實作的效果，參考 Liquid Glass 的設計方向，並非 Apple 的原生材質元件。應用介面目前為繁體中文；本儲存庫提供中英文文件。

## LSPosed 模式系統需求

- Android 12（API 31）以上。
- 已 Root，且安裝能在你的系統正常運作的 LSPosed。
- LSPosed 支援傳統 Xposed 模組 API 及新版 XSharedPreferences（API 93 以上）。
- 系統提供相容的 `ActivityTaskManagerService.getRecentTasks` 介面。

主要針對澎湃 OS 開發，不能保證所有 Android 版本或客製化桌面均相容。

## 下載與安裝

安裝隨附的 `CardHide-1.0-build6.apk`。以下為 LSPosed 模式步驟：

1. 安裝 APK。
2. 在 LSPosed 啟用「後台卡片隱藏」。
3. 作用域勾選「系統框架」（`android`），不需要勾選每個目標應用。
4. 重新開機，再開啟 CardHide。
5. 依提示取得應用清單權限，打開總開關並選擇應用。
6. 等約一秒，退出並重新開啟最近任務畫面。

**從 0.x 更新：**直接覆蓋安裝即可保留設定，請勿先解除安裝。若 LSPosed 提示重啟，依提示操作。官方釋出 APK 沿用同一簽章；自行換金鑰編譯的 APK 無法直接覆蓋。

## 應用清單權限

首次開啟（包含首次更新至有權限引導的版本）會顯示提示。點「前往權限設定」，找到「取得應用程式清單／獲取應用列表」，選擇「允許／始終允許」後返回。

若系統不接受直接跳轉，會退回應用資訊頁，請再進入「權限／其他權限」。亦可手動前往：

**設定 → 應用管理 → 後台卡片隱藏 → 權限 → 取得應用程式清單**

主畫面保留權限入口與重新載入功能。拒絕權限不會清除原有隱藏名單。清單空白或缺少應用時，先檢查這項澎湃 OS 額外權限。

## 背景與裁切

在「外觀 → 選擇圖片並裁切」選取圖片：

- 單指拖曳圖片調整構圖，雙指縮放。
- 拖曳四角調整裁切框。
- 預設「螢幕比例」適合目前螢幕方向；「自由比例」可任意調整長寬。
- 點「套用」才更新背景，取消會保留原圖。
- 自由比例圖片或旋轉後的畫面為填滿螢幕，可能再次置中裁掉邊緣。

圖片解碼最長邊上限為 3072 像素，儲存背景最長邊上限為 2560 像素。使用系統檔案選擇器，不需要廣泛相簿讀取權限。

## 原理與限制

模組在系統框架的 `ActivityTaskManagerService.getRecentTasks` 回傳後，建立移除所選套件項目的結果副本；**不呼叫 removeTask、force-stop 或 killProcess**，也不改寫系統保存的實際任務。

- 這不是永久保活工具；系統省電、記憶體回收與應用自身行為仍然有效。
- 不隱藏通知、桌面圖示或系統內部任務，不提供安全隔離。
- 選取僅套用至儲存設定的 Android 使用者，不跨應用分身、第二空間或工作設定檔。
- 桌面可能快取任務清單；退出再進入最近任務，必要時重新開機。
- ROM 若變更內部方法或回傳型別，功能可能失效。掛鉤失敗會保留原清單並記錄錯誤。

## 相容性與驗證狀態

使用者回報 **0.1.0 隱藏核心**在以下環境正常生效：

| 項目 | 環境 |
| --- | --- |
| 裝置 | 小米 Pad 8 Pro |
| 系統 | 澎湃 OS 4.0.0.44 |
| Root | KernelSU 32680-5 |
| 框架 | LSPosed 2.21 |

1.0 沿用同一隱藏核心。版本更名不代表所有新增介面已在該裝置驗證。

已完成：Java / DEX 建置、APK 簽章與對齊檢查、8 項篩選測試、裁切邊界案例及 10000 組隨機裁切範圍測試。

尚待裝置驗證：新版權限頁跳轉、完整應用清單、玻璃畫面、裁切手勢及不同 ROM 相容性。

## 停用與排錯

關閉總開關或取消勾選即可恢復卡片。完全停用時，在 LSPosed 停用模組並重新開機。

LSPosed 日誌中搜尋 `CardHide`：

- `getRecentTasks hooks=1` 或更大：找到掛鉤方法，仍需確認實際效果。
- `hooks=0` / `unsupported framework`：框架不相容。
- `filtering failed; returning original list`：回傳格式不符，保留原清單。

回報問題請附裝置、完整 OS / Android / LSPosed 版本、重現步驟及相關日誌；分享前移除個人資訊。

## 從原始碼建置（Windows）

需要 Python 3、JDK 21+，以及 Android SDK `platforms;android-36`、`build-tools;36.0.0`。將 Java 的 `bin` 加入 PATH。

本專案直接使用 SDK 工具打包，沒有 Gradle 專案或 wrapper。

```powershell
New-Item -ItemType Directory -Force app/libs | Out-Null
Invoke-WebRequest 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar' -OutFile app/libs/xposed-api-82.jar
$env:CARDHIDE_KEY_PASSWORD = '請換成你的簽章密碼'
python build.py --sdk 'D:\Android\Sdk' --keystore 'D:\Keys\cardhide.p12'
```

成品：`build/CardHide-1.0.apk`。首次執行會在指定位置建立簽章金鑰；後續更新請保留並使用同一金鑰。建置腳本同時執行篩選與裁切測試。

Xposed API JAR 僅用於編譯，不包含在 APK 中；執行期由 LSPosed 提供。官方釋出私鑰不在本儲存庫。

## 專案結構

```text
app/src/main/AndroidManifest.xml
app/src/main/java/tw/local/cardhide/
  MainActivity.java   # 管理頁、權限、外觀
  RecentHook.java     # LSPosed 系統框架掛鉤
  Selection.java      # 套件與使用者篩選
  GlassSurface.java   # 玻璃外觀
  CropActivity.java   # 圖片裁切介面
  CropMath.java       # 裁切幾何
app/src/main/assets/xposed_init
app/src/main/res/
tests/
build.py
```

## 參考

- [小米應用清單權限適配](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1619)
- [小米權限設定跳轉](https://dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/)
- [LSPosed XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)
- [Apple 材質設計](https://developer.apple.com/design/human-interface-guidelines/materials)

本專案獨立實作，未包含原參考應用的 APK、程式碼或資產。
