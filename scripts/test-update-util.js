'use strict';

const assert = require('assert');
const path = require('path');
const util = require(path.join(__dirname, '..', 'update_util.js'));

function assets() {
  return [
    { name: 'AabInstalllHelp-1.0.3-android.apk', browser_download_url: 'https://example/1.0.3.apk' },
    { name: 'AabInstalllHelp-1.0.3-windows-x64.exe', browser_download_url: 'https://example/1.0.3.exe' },
    { name: 'AabInstalllHelp-1.0.4-android.apk', browser_download_url: 'https://example/1.0.4.apk' },
    { name: 'AabInstalllHelp-1.0.4-windows-x64.exe', browser_download_url: 'https://example/1.0.4.exe' },
    { name: 'AabInstalllHelp-1.0.4-macos.dmg', browser_download_url: 'https://example/1.0.4.dmg' },
    { name: 'SHA256SUMS.txt', browser_download_url: 'https://example/SHA256SUMS.txt' }
  ];
}

assert.strictEqual(util.versionIsNewer('1.0.4', '1.0.3'), true);
assert.strictEqual(util.versionIsNewer('1.0.4', '1.0.4'), false);
assert.strictEqual(util.versionIsNewer('1.0.3', '1.0.4'), false);
assert.strictEqual(util.versionIsNewer('2.0.0', '1.9.9'), true);

assert.strictEqual(util.currentUpdateKind('win32'), 'win');
assert.strictEqual(util.currentUpdateKind('darwin'), 'mac');
assert.strictEqual(util.currentUpdateKind('linux'), 'linux');

assert.strictEqual(util.assetKindFromName('AabInstalllHelp-1.0.4-windows-x64.exe'), 'win');
assert.strictEqual(util.assetKindFromName('AabInstalllHelp-1.0.4-macos.dmg'), 'mac');
assert.strictEqual(util.assetKindFromName('AabInstalllHelp-1.0.4-android.apk'), 'android');
assert.strictEqual(util.versionFromAssetName('AabInstalllHelp-1.0.4-windows-x64.exe'), '1.0.4');

const mixed = assets();
const win = util.pickReleaseAsset(mixed, 'win', '1.0.4');
assert.ok(win);
assert.strictEqual(win.name, 'AabInstalllHelp-1.0.4-windows-x64.exe');

const apk = util.pickReleaseAsset(mixed, 'android', '1.0.4');
assert.ok(apk);
assert.strictEqual(apk.name, 'AabInstalllHelp-1.0.4-android.apk');

const mac = util.pickReleaseAsset(mixed, 'mac', '1.0.4');
assert.ok(mac);
assert.strictEqual(mac.name, 'AabInstalllHelp-1.0.4-macos.dmg');

assert.strictEqual(util.pickReleaseAsset(mixed, 'win', '1.0.3').name, 'AabInstalllHelp-1.0.3-windows-x64.exe');
assert.strictEqual(util.pickReleaseAsset(mixed, 'linux', '1.0.4'), null);
assert.strictEqual(util.pickReleaseAsset(mixed, 'win', '9.9.9'), null);
assert.strictEqual(util.pickReleaseAsset(mixed, 'sum', '1.0.4').name, 'SHA256SUMS.txt');

assert.strictEqual(util.progressPercent(50, 100), 50);
assert.strictEqual(util.progressPercent(0, 0), -1);
assert.ok(util.formatByteSize(70.7 * 1024 * 1024).indexOf('MB') >= 0);

console.log('update_util tests passed');
