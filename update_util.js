'use strict';

function versionIsNewer(remote, local) {
  function parts(text) {
    return String(text).split('.').map(function (p) {
      const n = parseInt(String(p).replace(/[^0-9]/g, ''), 10);
      return isNaN(n) ? 0 : n;
    });
  }
  const a = parts(remote);
  const b = parts(local);
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    const left = i < a.length ? a[i] : 0;
    const right = i < b.length ? b[i] : 0;
    if (left !== right) return left > right;
  }
  return false;
}

function currentUpdateKind(platform) {
  if (platform === 'darwin') return 'mac';
  if (platform === 'win32') return 'win';
  if (platform === 'linux') return 'linux';
  return '';
}

function assetKindFromName(name) {
  const n = String(name || '');
  if (/windows-x64\.exe$/i.test(n)) return 'win';
  if (/macos.*\.dmg$/i.test(n)) return 'mac';
  if (/linux.*\.(AppImage|deb|rpm)$/i.test(n)) return 'linux';
  if (/-android\.apk$/i.test(n)) return 'android';
  if (/^SHA256SUMS\.txt$/i.test(n)) return 'sum';
  return '';
}

function versionFromAssetName(name) {
  const m = String(name || '').match(/AabInstalllHelp-(\d+(?:\.\d+)*)/i);
  return m ? m[1] : '';
}

function pickReleaseAsset(assets, kind, version) {
  if (!Array.isArray(assets)) return null;
  if (kind === 'sum') {
    for (let i = 0; i < assets.length; i++) {
      const name = assets[i] && assets[i].name ? String(assets[i].name) : '';
      if (/^SHA256SUMS\.txt$/i.test(name)) return assets[i];
    }
    return null;
  }
  if (!kind) return null;
  const expectedVersion = version ? String(version) : '';
  for (let i = 0; i < assets.length; i++) {
    const asset = assets[i];
    const name = asset && asset.name ? String(asset.name) : '';
    if (assetKindFromName(name) !== kind) continue;
    if (expectedVersion && versionFromAssetName(name) !== expectedVersion) continue;
    return asset;
  }
  return null;
}

function formatByteSize(bytes) {
  let n = Number(bytes);
  if (!isFinite(n) || n < 0) n = 0;
  if (n < 1024) return Math.round(n) + ' B';
  const kb = n / 1024;
  if (kb < 1024) return kb.toFixed(1) + ' KB';
  const mb = kb / 1024;
  if (mb < 1024) return mb.toFixed(1) + ' MB';
  return (mb / 1024).toFixed(1) + ' GB';
}

function progressPercent(received, total) {
  if (!total || total <= 0) return -1;
  let pct = Math.floor((Number(received) / Number(total)) * 100);
  if (pct < 0) return 0;
  if (pct > 100) return 100;
  return pct;
}

function platformUpdateHint(kind) {
  if (kind === 'win') return 'Windows 安装包';
  if (kind === 'mac') return 'macOS dmg';
  if (kind === 'linux') return 'Linux 安装包';
  if (kind === 'android') return 'Android APK';
  return '当前平台安装包';
}

module.exports = {
  versionIsNewer: versionIsNewer,
  currentUpdateKind: currentUpdateKind,
  assetKindFromName: assetKindFromName,
  versionFromAssetName: versionFromAssetName,
  pickReleaseAsset: pickReleaseAsset,
  formatByteSize: formatByteSize,
  progressPercent: progressPercent,
  platformUpdateHint: platformUpdateHint
};
