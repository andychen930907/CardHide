# 1.0 build 7 — HyperOS後台卡片隱藏

首次啟用 Root / LSPosed 後不再提供自動重啟按鈕，改為不可取消的手動重啟提示。Shizuku 新增「檢測功能狀態」，會短暫恢復並重新隱藏已選任務，再讀取系統最近任務確認兩次變化。頂部「／個人空間」已移除。

若檢測顯示「系統層正常」，但澎湃桌面仍顯示卡片，代表桌面使用快取或廠商私有任務清單；此時應使用 Root / LSPosed。

Root first-enable now shows a non-cancelable manual-reboot notice. Shizuku gains an active restore/hide round-trip status test. The “/ Personal Space” header text is removed.
