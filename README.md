# AabInstallHelp

### 用途：

该项目主要是一个简单的安卓平台的 `aab` 安装包辅助安装软件，软件内置了 `bundletool` 工具和 `adb` 程序包，可以直接简单的选择一个目标安装包，即可自动完成安装流程，主要为了方便非研发人员安装 `aab` 格式的安装包。

大致的安装示意情况如下：

![image](image/aab_install_soft.gif)



项目是使用 [Electron](https://www.electronjs.org/) 进行开发（特点是：使用 js，html 和 css 构建跨平台的桌面应用），目前该工具支持输出 mac 和 window 平台的安装包。

### 构建流程
1. 确认本地有 node 和 npm 环境，如果没有需要先配置：[配置方式](https://nodejs.org/en/download/)

```
~ » node -v
v13.4.0

~ » npm -v
6.14.4
```

2. 如果本地已有 node 和 npm 环境，代码 clone 到本地之后，进入到项目根目录，执行以下命令即可运行

```
// 1、初始化项目
~ » npm install

// 2、mac 平台运行方式
~ » npm run start 

// 3、window 平台运行方式
~ » npm run start_win

// 初次构建，两个命令也可以一起运行，比如 mac
~ » npm install && npm run start
```

### 最终安装包位置（根目录 `packages/`）

```
packages/windows/   Windows 安装包 .exe
packages/android/   Android APK
packages/macos/     macOS dmg
packages/SHA256SUMS.txt
```

`release/` 和 `android/app/build/` 只是中间产物，不要从那里分发。

### 打包方式
1. mac 平台（中间产物在 `release/`，请再拷到 `packages/macos/` 或直接 `pack_all.bat`）
```
npm run elebuild_mac
```

2. window 平台
```
npm run elebuild_win
```

3. Windows + Android 打出最终包（写入 `packages/windows`、`packages/android`）
```
pack_all.bat
```

4. 发布到 GitHub Release（需 `gh auth login`）
```
publish_github_release.bat
```

版本号只写在根目录 `package.json`（桌面、Android `versionName`/`versionCode`、安装包文件名都读它）。`pack_all.bat` **不会**改版本。只有 `publish_github_release.bat` 发版时自增（默认 patch：`1.0.3` → `1.0.4`），然后打包、提交、打 `v*` tag。可用 `/minor`、`/major`；`/skip-pack` 或 `/no-bump` 则不自增。

Android 工程在 `android/`。需要本机 Android SDK；`local.properties` 的 `sdk.dir` 或环境变量 `ANDROID_HOME`。手机 APK 启动后扫描共享存储中的 `.aab`，用与桌面相同的 debug 测试签名安装。

应用内「检查更新」读取 GitHub `releases/latest`，确认后才下载。draft Release 不会作为更新源。macOS dmg 由 tag `v*` 触发的 Actions 补传到同一 Release。

### 签名说明

所有 aab 安装时都会使用内置的 Android debug 测试签名（`assets_common/debug.keystore`），不再读取或内置任何正式签名。

因此本工具只适合本地/测试安装，不能用于覆盖已用正式签名安装的包，也不适合作为上架签名。启动 Activity 会从 aab 清单中解析。

