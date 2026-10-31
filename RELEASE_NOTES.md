# 1.0 build 6 — HyperOS後台卡片隱藏

修正 KernelSU 自動重啟命令；Shizuku 服務重新啟動後會自動重連並恢復已儲存的隱藏設定；橫屏改成雙欄版面，控制區可捲動，應用清單不再被壓縮。

裝置重開機後，無 Root 模式仍須先透過無線偵錯啟動 Shizuku。Shizuku 可用後，應用會重新套用先前已啟用的設定。

Fix KernelSU reboot commands, automatically reconnect and restore settings when Shizuku returns, and use a two-column landscape layout. After a device reboot, wireless-debugging Shizuku must still be started first.

See README.md / README.en.md for setup, compatibility and recovery limitations.
