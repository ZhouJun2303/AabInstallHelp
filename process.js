// 渲染进程（web page）
const ipcRenderer = require('electron').ipcRenderer;
const remote = require('electron').remote;
const fs = require('fs');
const path = require('path');

const AAB_CANDIDATE_LIMIT = 100;
window.aabFilePath = "";
var connectdeviceSelect = document.getElementById("connectdevice");
var aabHistorySelect = document.getElementById("aabhistory");
connectdeviceSelect.addEventListener('change', () => {
  RefreshSelectConnectDevice();
});
aabHistorySelect.addEventListener('change', () => {
  OnAabHistoryChange();
});

RefreshSelectConnectDevice = function () {
  var select = document.getElementById("connectdevice");
  ipcRenderer.send('OnDeviceSeletChange', select.value);
}
RefreshSelectConnectDevice();

open_select_file = function () {
  ipcRenderer.send('open_file_select');
}

function getAabHistoryFilePath() {
  return path.join(remote.app.getPath('userData'), 'aab-history.json');
}

function isUsableAabFile(filepath) {
  if (!filepath) {
    return false;
  }
  var lower = String(filepath).toLowerCase();
  if (lower.length < 4 || lower.substring(lower.length - 4) !== '.aab') {
    return false;
  }
  try {
    return fs.existsSync(filepath) && fs.statSync(filepath).isFile();
  } catch (err) {
    return false;
  }
}

function isUsableDir(dir) {
  try {
    return !!dir && fs.existsSync(dir) && fs.statSync(dir).isDirectory();
  } catch (err) {
    return false;
  }
}

function emptyAabHistory() {
  return { lastPath: '', lastDir: '' };
}

function loadAabHistory() {
  try {
    var raw = fs.readFileSync(getAabHistoryFilePath(), 'utf8');
    var data = JSON.parse(raw);
    var lastPath = data.lastPath || '';
    var lastDir = data.lastDir || '';
    if (!lastPath && Array.isArray(data.items) && data.items.length) {
      var first = data.items[0];
      lastPath = typeof first === 'string' ? first : (first && first.path) || '';
    }
    if (!lastDir && lastPath) {
      lastDir = path.dirname(lastPath);
    }
    return {
      lastPath: lastPath,
      lastDir: lastDir
    };
  } catch (err) {
    return emptyAabHistory();
  }
}

function saveAabHistory(history) {
  try {
    var filePath = getAabHistoryFilePath();
    var dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(filePath, JSON.stringify({
      lastPath: history.lastPath || '',
      lastDir: history.lastDir || ''
    }, null, 2), 'utf8');
  } catch (err) {
    console.error('保存 aab 历史记录失败:', err);
  }
}

function listAabInDir(dir) {
  var result = [];
  if (!isUsableDir(dir)) {
    return result;
  }
  try {
    var names = fs.readdirSync(dir);
    names.forEach(function (name) {
      var full = path.join(dir, name);
      if (!isUsableAabFile(full)) {
        return;
      }
      var st = fs.statSync(full);
      result.push({
        path: full,
        name: name,
        mtime: st.mtimeMs || st.mtime.getTime()
      });
    });
    result.sort(function (a, b) {
      return b.mtime - a.mtime;
    });
    if (result.length > AAB_CANDIDATE_LIMIT) {
      result = result.slice(0, AAB_CANDIDATE_LIMIT);
    }
  } catch (err) {
    console.error('列举目录 aab 失败:', err);
  }
  return result;
}

function resolveHistoryDir(history) {
  if (history.lastDir && isUsableDir(history.lastDir)) {
    return history.lastDir;
  }
  if (history.lastPath) {
    var dir = path.dirname(history.lastPath);
    if (isUsableDir(dir)) {
      return dir;
    }
  }
  return history.lastDir || '';
}

function renderAabHistorySelect(items, selected, dir) {
  var select = document.getElementById("aabhistory");
  var hint = document.getElementById("aabdirhint");
  select.innerHTML = "";
  if (!items.length) {
    var emptyOption = document.createElement("option");
    emptyOption.value = "";
    if (!dir) {
      emptyOption.textContent = "暂无候选文件";
      hint.textContent = "还没有记录过 aab 目录，请先拖入或点击选择";
    } else if (!isUsableDir(dir)) {
      emptyOption.textContent = "目录不存在";
      hint.textContent = "目录不存在：" + dir;
    } else {
      emptyOption.textContent = "目录中没有 aab 文件";
      hint.textContent = "目录中没有 aab 文件：" + dir;
    }
    select.appendChild(emptyOption);
    select.disabled = true;
    return;
  }
  select.disabled = false;
  items.forEach(function (item, i) {
    var option = document.createElement("option");
    option.value = item.path;
    option.textContent = i === 0 ? item.name + "（最新）" : item.name;
    option.title = item.path;
    select.appendChild(option);
  });
  if (selected) {
    select.value = selected;
  }
  hint.textContent = "目录：" + dir;
}

function setCurrentAab(filepath) {
  window.aabFilePath = filepath || "";
  var message = document.getElementById('message');
  if (filepath) {
    message.innerText = "已选择文件：" + filepath + "\n\n";
  } else if (typeof setMessageInitStatus === 'function') {
    setMessageInitStatus();
  }
}

function refreshAabCandidates(preferredPath) {
  var history = loadAabHistory();
  if (preferredPath) {
    history.lastPath = preferredPath;
    history.lastDir = path.dirname(preferredPath);
  }
  var dir = resolveHistoryDir(history);
  var items = dir && isUsableDir(dir) ? listAabInDir(dir) : [];
  var selected = '';
  if (preferredPath && items.some(function (item) { return item.path === preferredPath; })) {
    selected = preferredPath;
  } else if (history.lastPath && items.some(function (item) { return item.path === history.lastPath; })) {
    selected = history.lastPath;
  } else if (items.length) {
    selected = items[0].path;
  }
  history.lastDir = dir || history.lastDir || '';
  history.lastPath = selected;
  saveAabHistory(history);
  renderAabHistorySelect(items, selected, history.lastDir);
  setCurrentAab(selected);
  return selected;
}

function rememberAab(filepath) {
  if (!isUsableAabFile(filepath)) {
    return false;
  }
  refreshAabCandidates(filepath);
  return true;
}

function OnAabHistoryChange() {
  var filepath = document.getElementById("aabhistory").value;
  if (!filepath) {
    return;
  }
  if (!isUsableAabFile(filepath)) {
    alert("文件不存在，已从候选列表中移除：\n" + filepath);
    refreshAabCandidates();
    return;
  }
  rememberAab(filepath);
}

RefreshAabCandidates = function () {
  refreshAabCandidates(window.aabFilePath);
};

function initAabHistory() {
  refreshAabCandidates();
}

start_process_aab = function () {
  const log = document.getElementById('log');
  log.innerHTML = "正在安装：<br>";
  rememberAab(window.aabFilePath);
  InstallAAb();
};

ipcRenderer.on('on_install_rsp', function (event, arg) {
  console.log("接收到主进程发送的消息：" + arg);
  const log = document.getElementById('log');
  log.innerText = log.innerText + arg;
  window.scrollTo(0, document.body.scrollHeight);
});

function showSelectAabFile() {
  var options = {
    filters: [{ name: 'aab', extensions: ['aab'] }],
    properties: ['openFile']
  }

  remote.dialog.showOpenDialog(options)
    .then((res) => {
      if (res.canceled) {
        return
      }
      const filenames = res.filePaths;
      var selected = filenames[0];
      if (!isUsableAabFile(selected)) {
        alert("选择的 aab 文件不存在或不可用：\n" + selected);
        refreshAabCandidates();
        return;
      }

      window.aabFilePath = selected;
      rememberAab(selected);

      const message = document.getElementById('message');
      message.innerText = "已选择文件：" + window.aabFilePath + "\n\n"

      start_process_aab();
    });
}

ipcRenderer.on('onDeviceList', function (event, deviceList) {
  console.log("接收到主进程发送的消息：" + "onDeviceList:" + deviceList);
  var select = document.getElementById("connectdevice");
  select.innerHTML = "";
  deviceList.forEach(function (device) {
    var option = document.createElement("option");
    option.value = device.device_id;
    option.textContent = device.device_id + "|" + device.device_name;
    select.appendChild(option);
  });
});

RefreshConnectDevice = function () {
  ipcRenderer.send('RefreshConnectDevice');
};
InstallAAb = function () {
  var filepath = window.aabFilePath;
  if (!filepath) {
    alert("请先选择 aab 文件");
    return;
  }
  if (!isUsableAabFile(filepath)) {
    alert("文件不存在，已从候选列表中移除：\n" + filepath);
    refreshAabCandidates();
    return;
  }
  ipcRenderer.send('install_aab', filepath);
};

ClearLog = function () {
  const log = document.getElementById('log');
  log.innerText = "0.0";
}

RefreshConnectDevice();
initAabHistory();
