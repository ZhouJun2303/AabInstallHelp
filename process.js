// 渲染进程（web page）
const ipcRenderer = require('electron').ipcRenderer;
const remote = require('electron').remote;

var connectdeviceSelect = document.getElementById("connectdevice");
// 添加事件监听器
connectdeviceSelect.addEventListener('change', () => {
  RefreshSelectConnectDevice();
});

RefreshSelectConnectDevice = function () {
  var select = document.getElementById("connectdevice");
  ipcRenderer.send('OnDeviceSeletChange', select.value);
}
RefreshSelectConnectDevice(); 

open_select_file = function () {
  ipcRenderer.send('open_file_select');
}

// 把需要处理的文件路径传递给主进程
start_process_aab = function (file_path) {

  const log = document.getElementById('log');

  log.innerHTML = "正在安装：<br>";

  ipcRenderer.send('install_aab', file_path);
};

// 监听主进程发送过来的安装结果
ipcRenderer.on('on_install_rsp', function (event, arg) {

  console.log("接收到主进程发送的消息：" + arg);

  const log = document.getElementById('log');
  log.innerText = log.innerText + arg;

  // 自动滚动到页面的底部
  window.scrollTo(0, document.body.scrollHeight);
});

// 处理打开文件选择框，以获取需要处理安装的 aab 文件
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

      const aabFilePath = filenames[0];

      const message = document.getElementById('message');
      message.innerText = "已选择文件：" + aabFilePath + "\n\n"

      start_process_aab(aabFilePath);
    });
}


// 监听主进程发送过来的设备
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
