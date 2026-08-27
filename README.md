# AabInstallHelp

把 `.aab` 安装到已连接的 Android 设备。内置 bundletool 和 adb，面向非研发人员的本地/测试安装。

桌面端（Windows / macOS）用 Electron；另有 Android APK，可扫描共享存储中的 `.aab` 安装。一律使用内置 debug 测试签名，不能覆盖商店正式包，也不能用于上架。

## 运行

需要 [Node.js](https://nodejs.org/en/download/)。

```
npm install
npm run start        # macOS
npm run start_win    # Windows
```

## 打包

最终产物在 `packages/`。`release/` 和 `android/app/build/` 是中间产物，不要从那里分发。

```
packages/windows/   .exe
packages/android/   .apk
packages/macos/     .dmg
packages/SHA256SUMS.txt
```

```
npm run elebuild_mac           # macOS
npm run elebuild_win           # Windows
pack_all.bat                   # Windows + Android → packages/
pack_all.bat /win
pack_all.bat /android
publish_github_release.bat     # 需 gh auth login
```

版本号只写在根目录 `package.json`。`pack_all.bat` 不改版本，但会清掉 `packages/` 里其他版本的文件。`publish_github_release.bat` 发版时默认 patch 自增，然后打包、提交、打 `v*` tag，且只上传当前版本。可用 `/minor`、`/major`；`/skip-pack` 或 `/no-bump` 则不自增。

Android 需要本机 SDK（`ANDROID_HOME` 或 `android/local.properties` 的 `sdk.dir`），以及 JDK 17+。

应用内「检查更新」读 GitHub `releases/latest`，按当前系统取对应包（Windows `.exe` / macOS `.dmg` / Android `.apk`），确认后才下载，下载过程显示进度。draft 不会作为更新源。macOS dmg 由 tag `v*` 的 Actions 补传到同一 Release，并回写 `SHA256SUMS.txt`。

## 签名

安装一律使用 `assets_common/debug.keystore`。启动 Activity 从 aab 清单解析。
