String.prototype.endswith = function (endStr) {
    var d = this.length - endStr.length;
    return (d >= 0 && this.lastIndexOf(endStr) == d)
}

const holder = document.getElementById('holder');
const message = document.getElementById('message');

holder.ondragenter = holder.ondragover = (event) => {
    event.preventDefault();
    holder.className = "dropify-wrapper-ondrag drop-zone";
    message.innerHTML = "松开以选择该 aab";
}

holder.ondragend = holder.ondragleave = (event) => {
    event.preventDefault();
    setMessageInitStatus();
}

holder.ondrop = (e) => {
    e.preventDefault();
    for (let f of e.dataTransfer.files) {
        var filepath = f.path;
        if (!filepath.endswith('.aab')) {
            setMessageInitStatus();
            alert('抱歉，不能处理非 aab 后缀的文件：' + filepath);
            return false;
        }
        holder.className = "drop-zone";
        window.aabFilePath = filepath;
        if (typeof rememberAab === 'function') {
            rememberAab(filepath);
        }
        setMessageInitStatus();
        if (typeof updateActionState === 'function') {
            updateActionState();
        }
    }
}

function setMessageInitStatus() {
    holder.className = "drop-zone";
    if (window.aabFilePath) {
        message.innerHTML = "已选择文件：" + window.aabFilePath;
        return;
    }
    message.innerHTML = "点击或将 aab 拖到这里选择文件";
}
