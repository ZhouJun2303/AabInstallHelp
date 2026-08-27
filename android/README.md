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

`runtime/` 下的 `aapt2-arm64-v8a`、`aapt2-x86_64`、`debug.keystore` 会在编译时拷进 APK。转换走 bundletool 的 `BuildApksCommand`（进程内），aapt2 作为可执行文件抽出后调用。

本机 `assembleDebug` 已通过：bundletool 作为 Gradle 依赖打进 APK（debug APK 约 90MB）。真机转换仍取决于 ART 能否跑 bundletool + 可执行 aapt2。若失败，日志会有完整异常，再考虑 JRE 回退。

## 权限

- 所有文件访问（扫共享存储里的 aab）
- 安装未知应用
- 查询已装应用签名（冲突时提示卸载）

微信/QQ 私有目录扫不到，请用「选择文件」。
