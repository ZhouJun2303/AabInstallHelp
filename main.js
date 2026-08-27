const { app, BrowserWindow, ipcMain, ipcRenderer, dialog, nativeImage, clipboard } = require('electron');
const { Menu } = require('electron');
const exec = require('child_process').exec;
const xmlReader = require('xmlreader');
const path = require('path');
const readFile = require('fs');

let UseDevicesId = "";
// 最近一次来自渲染进程的 ipc event，供 AabInfo 等无 event 场景回落到 UI
let lastUiEvent = null;
// 应用的主窗口
let mainWindow;
const EXEC_OPTS = { maxBuffer: 20 * 1024 * 1024 };
// 所有 aab 统一使用 Android debug 测试签名，避免内置正式签名泄露
const DEBUG_SIGN = {
  file: 'debug.keystore',
  ks_pass: 'android',
  alias: 'androiddebugkey',
  key_pass: 'android'
};

// 创建主窗口
function createWindow() {
  // 创建浏览器窗口
  mainWindow = new BrowserWindow({
    title: "安装 aab 程序包",
    minHeight: 600,
    minWidth: 800,
    width: 800,
    height: 600,
    webPreferences: {
      nodeIntegration: true
    }
  });

  // 主进程中使用
  // mainWindow.webContents.openDevTools();

  // 加载index.html文件
  mainWindow.loadFile("index.html");

  console.log('是否是 window 平台：' + isWinOS());
  console.log(`平台信息：` + process.platform);
  console.log('是否是开发环境：' + app.isPackaged);
  console.log(`资源路径：${process.resourcesPath}`)
}


app.on('ready', createWindow)

app.on("window-all-closed", function () {
  app.quit();
})

// 处理选择文件
ipcMain.on('open_file_select', function (event, arg) {

  var options = {
    title: '选择 aab 程序包',
    filters: [{ name: 'aab', extensions: ['aab'] }],
    properties: ['openFile']
  }

  dialog.showOpenDialog(mainWindow, options)
    .then((res) => {
      if (res.canceled) {
        return
      }
      const filenames = res.filePaths;
      console.log(`选择文件：${filenames}`);
    });
});

// 接收渲染进程发送过来的消息，可以通过：on_install_rsp 发送消息回去
ipcMain.on('install_aab', function (event, arg) {
  console.log("请求处理安装 aab 安装包: " + arg);

  // console.log(getJavaPath());

  // 开始 aab 安装处理流程
  parseAabContent(event, arg);
});

// 接收渲染进程发送过来的消息，可以通过：on_install_rsp 发送消息回去
ipcMain.on('RefreshConnectDevice', function (event, arg) {
  RefreshConnectDevice(event, arg);
});

// 接收渲染进程发送过来的消息，可以通过：on_install_rsp 发送消息回去
ipcMain.on('OnDeviceSeletChange', function (event, arg) {
  UseDevicesIdRefresh(event, arg);
});

function UseDevicesIdRefresh(event, arg) {
  if (UseDevicesId == arg) return;
  UseDevicesId = arg;
  sendMsgToUI(event, "当前选择设备刷新： " + UseDevicesId);
}

/**
 * 发送消息到 UI 界面进行展示。
 * 兼容 (event, msg) 以及历史上只传文案的 sendMsgToUI(msg)。
 * @param {Electron.IpcMainEvent|string} event 
 * @param {*} msg 
 */
function sendMsgToUI(event, msg) {
  if (typeof event === 'string' && (arguments.length < 2 || msg === undefined)) {
    msg = event;
    event = lastUiEvent;
  } else if (event && typeof event === 'object' && event.sender) {
    lastUiEvent = event;
  }

  const text = `[aab 安装]${msg}\n`;
  const targets = [];
  if (event && event.sender) {
    targets.push(event.sender);
  }
  if (mainWindow && !mainWindow.isDestroyed() && mainWindow.webContents) {
    targets.push(mainWindow.webContents);
  }

  for (let i = 0; i < targets.length; i++) {
    const sender = targets[i];
    try {
      if (sender.isDestroyed && sender.isDestroyed()) {
        continue;
      }
      sender.send('on_install_rsp', text);
      return;
    } catch (err) {
      console.error('sendMsgToUI failed:', err);
    }
  }
  console.log(text);
}

function quoteArg(value) {
  const s = value == null ? '' : String(value);
  if (s === '') {
    return '""';
  }
  if (!/[ \t"&<>|^()]/.test(s)) {
    return s;
  }
  return '"' + s.replace(/"/g, '""') + '"';
}

function getAssetsPath() {
  let assets_path = app.isPackaged ? `${process.resourcesPath}/assets` : `${app.getAppPath()}/assets`;
  return assets_path;
}

function getBundletoolJarPath() {
  let jar_path = `${getAssetsPath()}/bundletool-all-1.18.3.jar`;
  return `${quoteArg(getJavaPath())} -jar ${quoteArg(jar_path)}`;
}

function getJavaPath() {
  let java_bin_path = `${getAssetsPath()}/java/bin`;
  let java_path = isWinOS() ? `${java_bin_path}/java.exe` : `${java_bin_path}/java`;
  return java_path;
}

function getAdbPath() {
  let adb_path = isWinOS() ? `${getAssetsPath()}/adb.exe` : `${getAssetsPath()}/adb`;
  return adb_path;
}

function getInstallTempPath() {
  let install_temp_path = app.isPackaged ? `${process.resourcesPath}/install_temp` : `${app.getAppPath()}/install_temp`;
  return install_temp_path;
}

function extractManifestXml(stdout) {
  if (stdout == null) {
    return '';
  }
  const text = String(stdout);
  const xmlDecl = text.indexOf('<?xml');
  if (xmlDecl >= 0) {
    return text.slice(xmlDecl).trim();
  }
  const manifestStart = text.indexOf('<manifest');
  if (manifestStart >= 0) {
    return text.slice(manifestStart).trim();
  }
  return text.trim();
}

function resolveAndroidName(pkg, name) {
  if (name == null || name === '') {
    return null;
  }
  const n = String(name);
  if (n.startsWith('.')) {
    return pkg + n;
  }
  if (n.indexOf('.') === -1) {
    return pkg + '.' + n;
  }
  return n;
}

function extractLauncherComponent(manifestXml, pkg) {
  if (manifestXml == null || pkg == null || pkg === '') {
    return null;
  }
  const xml = String(manifestXml);
  const blockRe = /<(activity-alias|activity)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/\1>)/gi;
  let match;
  while ((match = blockRe.exec(xml)) !== null) {
    const attrs = match[2] || '';
    const body = match[3] || '';
    const enabled = attrs.match(/android:enabled\s*=\s*["']([^"']+)["']/i);
    if (enabled && String(enabled[1]).toLowerCase() === 'false') {
      continue;
    }
    const nameMatch = attrs.match(/android:name\s*=\s*["']([^"']+)["']/i);
    if (!nameMatch) {
      continue;
    }
    const hasMain = /android:name\s*=\s*["']android\.intent\.action\.MAIN["']/.test(body);
    const hasLauncher = /android:name\s*=\s*["']android\.intent\.category\.LAUNCHER["']/.test(body);
    if (hasMain && hasLauncher) {
      return resolveAndroidName(pkg, nameMatch[1]);
    }
  }
  return null;
}

/**
 * 第一步：解析 aab 文件，用于获取到应用相关的信息
 *
 * @param {Electron.IpcMainEvent} event
 * @param {*} aab_file_path
 */
function parseAabContent(event, aab_file_path) {
  if (null == aab_file_path || '' == aab_file_path) {
    sendMsgToUI(event, "未选择文件");
    return;
  }
  if (!readFile.existsSync(aab_file_path)) {
    let errorMsg = `选择的 aab 文件不存在：${aab_file_path}`;
    sendMsgToUI(event, errorMsg);
    tipsInstallError(errorMsg);
    return;
  }
  if (UseDevicesId == null || UseDevicesId == "") {
    sendMsgToUI(event, "未选择设备");
    return;
  }
  sendMsgToUI(event, `当前选择设备： ${UseDevicesId}`);
  sendMsgToUI(event, `1、正在进行 aab 文件解析：${path.basename(aab_file_path)}`);

  let bundletool_jar_path = getBundletoolJarPath();

  let cmd = `${bundletool_jar_path} dump manifest --bundle=${quoteArg(aab_file_path)}`;

  exec(cmd, EXEC_OPTS, (err, stdout, stderr) => {
    if (stderr && String(stderr).trim() !== '') {
      sendMsgToUI(event, `dump manifest 附加输出：${stderr}`);
    }

    const manifestXml = extractManifestXml(stdout);
    if (!manifestXml) {
      let errorMsg = `获取 aab manifest 文件信息出错，错误信息：${err || stderr || '输出为空'}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    xmlReader.read(manifestXml, function (errors, response) {
      if (null !== errors) {
        let errorMsg = `解析 aab 的清单内容出错：${errors}`;
        log(errorMsg);
        sendMsgToUI(event, errorMsg);
        tipsInstallError(errorMsg);
        return;
      }

      try {
        let attrs = response.manifest.attributes();
        let app_pkg = attrs.package;
        let app_vname = attrs['android:versionName'];
        let app_vcode = attrs['android:versionCode'];

        if (!app_pkg) {
          let errorMsg = '解析 aab 的清单内容出错：未读取到包名';
          sendMsgToUI(event, errorMsg);
          tipsInstallError(errorMsg);
          return;
        }

        let launcher = extractLauncherComponent(manifestXml, app_pkg);
        let aabInfo = new AabInfo(app_pkg, app_vname, app_vcode, launcher);
        let aabParseRst = `aab 文件解析结果如下👉：\n应用包名：${aabInfo.pkg}，应用版本信息：${aabInfo.getAppVersionInfo()}`;
        if (launcher) {
          aabParseRst += `，启动 Activity：${launcher}`;
        }
        aabParseRst += `\n`;
        log(aabParseRst);
        sendMsgToUI(event, aabParseRst);
        generateSpecFile(event, aab_file_path, aabInfo);
      } catch (parseErr) {
        let errorMsg = `解析 aab 的清单内容出错：${parseErr}`;
        sendMsgToUI(event, errorMsg);
        tipsInstallError(errorMsg);
      }
    });
  });
}

/**
 * 第二步：生成设备描述文件
 * @param {Electron.IpcMainEvent} event 
 * @param {*} aab_file_path 
 * @param {AabInfo} aabInfo 
 */
function generateSpecFile(event, aab_file_path, aabInfo) {
  sendMsgToUI(event, `2、正在生成设备描述文件`);

  let install_temp_path = getInstallTempPath();

  // bundletool jar 包路径
  let bundletool_jar_path = getBundletoolJarPath();
  // aab 路径
  let adb_path = getAdbPath();

  // 设备描述文件路径
  let device_spec_file = `${install_temp_path}/device-spec.json`;

  // 1、生成连接设备对应的 spec 文件命令
  let gen_device_spec_cmd = `${bundletool_jar_path} get-device-spec --adb ${quoteArg(adb_path)} --output=${quoteArg(device_spec_file)} --overwrite --device-id=${quoteArg(UseDevicesId)}`;

  exec(gen_device_spec_cmd, EXEC_OPTS, (errE, stdout) => {
    if (null !== errE) {
      let errorMsg = `生成设备描述文件出错，错误信息：${errE}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    sendMsgToUI(event, `设备描述信息生成成功，文件路径👉：\n${device_spec_file}\n`);

    showDeviceSpecFile(event, aab_file_path, aabInfo, device_spec_file)
  });
}

/**
 * 第三步：读取生成的设备描述信息
 * 
 * @param {Electron.IpcMainEvent} event 
 * @param {*} aab_file_path 
 * @param {AabInfo} aabInfo 
 * @param {*} device_spec_file 
 */
function showDeviceSpecFile(event, aab_file_path, aabInfo, device_spec_file) {
  sendMsgToUI(event, `3、读取设备描述信息`);

  readFile.readFile(device_spec_file, 'utf-8', function (error, content) {
    if (error) {
      let errorMsg = `读取设备描述文件失败：${error}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    log(content);
    sendMsgToUI(event, `设备描述信息读取成功，内容如下👉：\n${content}\n`);
    buildApksFile(event, aab_file_path, aabInfo, device_spec_file);
  })
}

/**
 * 第四步：根据设备描述文件生成 apks，并安装到连接设备上
 * 
 * @param {Electron.IpcMainEvent} event 
 * @param {*} aab_file_path 
 * @param {AabInfo} aabInfo 
 * @param {*} device_spec_file 
 */
function buildApksFile(event, aab_file_path, aabInfo, device_spec_file) {
  sendMsgToUI(event, `4、正在使用 debug 测试签名生成 apks 文件`);

  let install_temp_path = getInstallTempPath();

  // bundletool jar 包路径
  let bundletool_jar_path = getBundletoolJarPath();

  // apks 文件路径
  let apks_file = `${install_temp_path}/app_bundle.apks`;
  let ks_file = `${getAssetsPath()}/${DEBUG_SIGN.file}`;

  log(`使用 debug 测试签名：${ks_file}`);

  // 根据 spec 文件生成 apks 文件
  let gen_apks_cmd = `${bundletool_jar_path} build-apks --bundle=${quoteArg(aab_file_path)} --device-spec=${quoteArg(device_spec_file)} --output=${quoteArg(apks_file)} --ks=${quoteArg(ks_file)} --ks-pass=pass:${DEBUG_SIGN.ks_pass} --ks-key-alias=${quoteArg(DEBUG_SIGN.alias)} --key-pass=pass:${DEBUG_SIGN.key_pass} --overwrite`;

  exec(gen_apks_cmd, EXEC_OPTS, (errE, stdout) => {
    if (null !== errE) {
      let errorMsg = `生成 apks 文件出错，错误信息：${errE}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    log(`生成 apks 文件成功，文件路径：${apks_file}`);

    sendMsgToUI(event, `生成 apks 文件成功，文件路径👉：\n${apks_file}\n`);
    installApkToDevice(event, aabInfo, apks_file);
  });
}

function isSignatureMismatchError(err) {
  const text = err == null ? '' : String(err);
  return text.indexOf('INSTALL_FAILED_UPDATE_INCOMPATIBLE') >= 0
    || text.indexOf('signatures do not match') >= 0;
}

/**
 * 第五步：将 apks 文件安装到设备中
 * 
 * @param {Electron.IpcMainEvent} event 
 * @param {AabInfo} aabInfo 
 * @param {*} apks_file 
 * @param {Boolean} retriedUninstall
 */
function installApkToDevice(event, aabInfo, apks_file, retriedUninstall) {
  sendMsgToUI(event, `5、正在将 apks 安装到设备中...`);

  // bundletool jar 包路径
  let bundletool_jar_path = getBundletoolJarPath();
  // aab 路径
  let adb_path = getAdbPath();

  // 3、安装 apks 到连接设备
  let install_apks_cmd = `${bundletool_jar_path} install-apks --adb ${quoteArg(adb_path)} --apks=${quoteArg(apks_file)} --device-id=${quoteArg(UseDevicesId)}`;

  exec(install_apks_cmd, EXEC_OPTS, (errE, stdout) => {
    if (null !== errE) {
      if (!retriedUninstall && isSignatureMismatchError(errE)) {
        confirmUninstallThenRetry(event, aabInfo, apks_file, errE);
        return;
      }
      let errorMsg = `安装 apks 文件到设备时出错，错误信息：${errE}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    sendMsgToUI(event, `已成功将 aab 程序包安装到设备中\n`);
    autoStartApplication(event, aabInfo);
  });
}

function confirmUninstallThenRetry(event, aabInfo, apks_file, installErr) {
  const pkg = aabInfo.pkg;
  const originalErr = installErr == null ? '' : String(installErr);
  sendMsgToUI(event, `设备上已安装的 ${pkg} 签名与当前 debug 测试签名不一致，无法覆盖安装`);

  showAppMessageBox({
    type: 'warning',
    title: '签名不一致',
    icon: nativeImage.createEmpty(),
    message: `设备上的 ${pkg} 签名与当前 debug 测试签名不一致，无法覆盖安装。`,
    detail: '继续将先卸载旧应用（应用数据会丢失），再重新安装。',
    buttons: ['卸载并重装', '复制错误信息', '取消'],
    defaultId: 0,
    cancelId: 2,
    noLink: true
  }).then(function (index) {
    if (index === 1) {
      if (originalErr) {
        clipboard.writeText(originalErr);
      }
      sendMsgToUI(event, '已复制原始错误信息');
      return;
    }
    if (index !== 0) {
      sendMsgToUI(event, '已取消安装');
      return;
    }
    uninstallThenRetryInstall(event, aabInfo, apks_file);
  }).catch(function (err) {
    console.error('confirmUninstallThenRetry failed:', err);
    tipsInstallError(originalErr || String(err));
  });
}

function uninstallThenRetryInstall(event, aabInfo, apks_file) {
  const pkg = aabInfo.pkg;
  const adb_path = getAdbPath();
  const uninstall_cmd = `${quoteArg(adb_path)} -s ${quoteArg(UseDevicesId)} uninstall ${quoteArg(pkg)}`;

  sendMsgToUI(event, `正在卸载设备上的旧应用：${pkg}`);
  exec(uninstall_cmd, EXEC_OPTS, (errE, stdout, stderr) => {
    const out = `${stdout || ''}\n${stderr || ''}`;
    if (!/Success/i.test(out)) {
      let errorMsg = `卸载旧应用失败：${errE || out.trim() || '未知错误'}`;
      sendMsgToUI(event, errorMsg);
      tipsInstallError(errorMsg);
      return;
    }

    sendMsgToUI(event, `旧应用已卸载，开始重新安装`);
    installApkToDevice(event, aabInfo, apks_file, true);
  });
}

/**
 * 第六步：自动启动刚安装完的应用程序
 * 
 * @param {Electron.IpcMainEvent} event 
 * @param {AabInfo} aabInfo 
 */
function autoStartApplication(event, aabInfo) {
  sendMsgToUI(event, `6、正在尝试自动启动应用，包名：${aabInfo.pkg}, 应用版本信息：${aabInfo.getAppVersionInfo()}`);

  let autoStartActivity = aabInfo.getAutoStartActivity();
  if (null == autoStartActivity) {
    sendMsgToUI(event, '抱歉无法自动识别打开应用，你可以手动打开应用进行测试');
    tipsInstallFinish(false, aabInfo);
    return;
  }

  // aab 路径
  let adb_path = getAdbPath();

  // 4、启动刚才安装好的应用
  let start_app_cmd = `${quoteArg(adb_path)} -s ${quoteArg(UseDevicesId)} shell am start -n ${quoteArg(autoStartActivity)} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER`;

  exec(start_app_cmd, EXEC_OPTS, (errE, stdout) => {
    if (null !== errE) {
      sendMsgToUI(event, `尝试自动启动应用出错，错误信息：${errE}`);
      tipsInstallFinish(false, aabInfo);
      return;
    }

    sendMsgToUI(event, `应用已自动启动，可以开始进行测试验收~~~\n`);
    tipsInstallFinish(true, aabInfo);
  });
}

/**
 * 弹出安装失败的提示框，利于给使用人员强制的提示
 * 
 * @param {*} msg 
 */
function showAppMessageBox(options) {
  const boxPromise = (mainWindow && !mainWindow.isDestroyed())
    ? dialog.showMessageBox(mainWindow, options)
    : dialog.showMessageBox(options);
  return Promise.resolve(boxPromise).then(function (result) {
    return result && typeof result.response === 'number' ? result.response : result;
  });
}

function tipsInstallError(msg) {
  const text = msg == null ? '' : String(msg);
  var options = {
    type: 'error',
    title: '安装出错',
    icon: nativeImage.createEmpty(),
    message: text,
    buttons: ['复制错误信息', '确定'],
    defaultId: 1,
    cancelId: 1,
    noLink: true
  };

  showAppMessageBox(options).then(function (index) {
    if (index === 0 && text) {
      clipboard.writeText(text);
    }
  }).catch(function (err) {
    console.error('tipsInstallError failed:', err);
  });
}

/**
 * 使用强烈的对话框提示已经完成安装
 * 
 * @param {Boolean} is_auto_start 
 * @param {AabInfo} aabInfo 
 */
function tipsInstallFinish(is_auto_start, aabInfo) {

  let version_info = `(应用包名：${aabInfo.pkg}, 版本信息：${aabInfo.getAppVersionInfo()})`;

  var options = {
    type: 'info',
    title: '温馨提示',
    icon: nativeImage.createEmpty(),
    message: is_auto_start ?
      `安装成功，应用已自动启动，可以开始进行测试验收~~~\n${version_info}` :
      `抱歉无法自动识别打开应用，你可以手动打开应用进行测试\n${version_info}`
  }

  dialog.showMessageBox(options);
}

// 判断是否是 window 平台
function isWinOS() {
  let os_info = process.platform;

  if (os_info.startsWith('win')) {
    return true;
  }

  return false;
}

function log(log_str) {
  if (log_str == null || log_str == '') {
    return;
  }

  console.log(log_str);
}

function RefreshConnectDevice(event, arg) {
  let adb_path = getAdbPath();
  let refreshconnectdevice_cmd = `${quoteArg(adb_path)} devices -l`;

  exec(refreshconnectdevice_cmd, EXEC_OPTS, (errE, stdout) => {
    if (null !== errE) {
      sendMsgToUI(event, `RefreshConnectDevice，错误信息：${errE}`);
      return;
    }
    var deviceIds = parseDevices(event, stdout);
    event.sender.send('onDeviceList', deviceIds);
  });
}

// 解析 adb devices 输出并提取设备信息
function parseDevices(event, output) {
  let lines = output.trim().split('\n'); // 分割输出的每一行
  let devices = [];
  UseDevicesIdRefresh(event, '');
  lines.forEach(line => {
    // 每行通常格式为 "设备ID         状态 product:设备名称 model:设备型号 device:aosp"
    let parts = line.split(' ');
    let deviceId = parts[0]; // 第一部分是设备 ID
    let status = parts[1]; // 第二部分是设备状态（device 或 offline 等）
    let productInfo = parts.slice(2).join(' '); // 其余部分包含 product 信息

    if (line.indexOf('offline') != -1) {
      return;
    }
    // 如果设备状态是 offline 或者设备名称为 Unknown，则跳过
    if (status == 'offline') {
      return; // 跳过 offline 设备
    }

    // 从 product 信息中提取设备名称（可以通过正则表达式提取 "product:设备名称"）
    let match = productInfo.match(/product:([^\s]+)/);
    let deviceName = match ? match[1] : 'Unknown'; // 如果匹配不到设备名称，默认返回 'Unknown'

    // 如果设备名称是 Unknown，则跳过该设备
    if (deviceName == 'Unknown') {
      return;
    }
    // 将有效的设备信息存入数组
    devices.push({ device_name: deviceName, device_id: deviceId });

    UseDevicesIdRefresh(event, deviceId);
  });
  return devices;
};

// aab 文件信息类
class AabInfo {
  constructor(pkg_v, vname_v, vcode_v, launcher_v) {
    this.pkg = pkg_v;
    this.vname = vname_v;
    this.vcode = vcode_v;
    this.launcher = launcher_v || null;
  }

  getAppVersionInfo() {
    return `${this.vname}.${this.vcode}`;
  }

  getAutoStartActivity() {
    if (this.launcher) {
      return this.pkg + '/' + this.launcher;
    }
    return null;
  }
}

