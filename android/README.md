# AabInstallHelp Android

手机上扫描 `.aab`，用与桌面相同的 `debug.keystore`（`androiddebugkey` / `android`）拆包并安装。

## 构建

需要 Android SDK（`ANDROID_HOME` 或本目录 `local.properties` 的 `sdk.dir`）和 **JDK 17+**（不要用 Java 8 的 `JAVA_HOME`）。`pack_all.bat` / `gradlew.bat` 会优先选用本机的 `jdk-17`、`jdk-21` 或 Android Studio JBR。

```
cd android
gradlew.bat :app:assembleRelease
```

Gradle 中间产物：`app/build/outputs/apk/release/app-release.apk`  
最终发布文件：仓库根目录 `packages/android/AabInstalllHelp-<version>-android.apk`（由根目录 `pack_all.bat` 拷入）。

## 运行时

转换走 bundletool 的 `BuildApksCommand`（进程内）。

- 签名：构建时从 `assets_common/debug.keystore` 导出 `debug-key.pk8` / `debug-cert.der`（ART 没有 JKS，不能直接读 keystore）。身份与桌面相同（`androiddebugkey` / `android`）。
- aapt2：`runtime/aapt2-*` 打进 `jniLibs` 成 `libaapt2.so`，运行时从 `nativeLibraryDir` 执行（应用 `filesDir` 在 Android 10+ 经常是 noexec）。

失败时安装日志会带完整堆栈。

## 权限

- 所有文件访问（扫共享存储里的 aab）
- 安装未知应用
- 查询已装应用签名（冲突时提示卸载）

微信/QQ 私有目录扫不到，请用「选择文件」。
