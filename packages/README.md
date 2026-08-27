# 最终构建产物

所有平台打好的安装包都放在这里，按平台分子目录。中间产物（electron-builder 的 `release/`、Gradle 的 `android/app/build/`）不要当发布文件用。

```
packages/
  windows/   AabInstalllHelp-<version>-windows-x64.exe
  android/   AabInstalllHelp-<version>-android.apk
  macos/     AabInstalllHelp-<version>-macos.dmg
  SHA256SUMS.txt
```

版本号来自根目录 `package.json`。`pack_all.bat` 只按当前版本拷贝，不改版本。发 GitHub Release 时由 `publish_github_release.bat` 自增后再打包。

生成本目录：

```
pack_all.bat
```

只打某一端：

```
pack_all.bat /win
pack_all.bat /android
```
