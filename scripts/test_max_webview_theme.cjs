const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/mrtt/client/MaxWebViewScripts.kt'), 'utf8');
const template = source.match(/return """([\s\S]*?)"""/)[1];
const auth = '{"token":"synthetic-auth-must-stay"}';
const storage = new Map([
  ['__oneme_auth', auth],
  ['__oneme_theme', JSON.stringify({colorScheme: 'system', colorTheme: 'space', fontScale: 'large'})],
]);
const root = {dataset: {}, style: {}};
let observers = 0;
let onMutation;
const sandbox = {
  window: {},
  document: {documentElement: root},
  localStorage: {getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value)},
  MutationObserver: class {
    constructor(callback) { observers++; onMutation = callback; }
    observe() {}
  },
};

for (const scheme of ['dark', 'dark', 'light']) {
  vm.runInNewContext(template.replace('$scheme', scheme), sandbox);
  assert.equal(root.dataset.colorScheme, scheme);
  assert.equal(root.style.colorScheme, scheme);
  assert.deepEqual(JSON.parse(storage.get('__oneme_theme')), {colorScheme: scheme, colorTheme: 'space', fontScale: 'large'});
  root.dataset.colorScheme = 'system';
  onMutation();
  assert.equal(root.dataset.colorScheme, scheme, 'MAX must keep the application theme after its own rerender');
  assert.equal(storage.get('__oneme_auth'), auth, 'applying the theme must preserve the login');
}
assert.equal(observers, 1, 'do not accumulate theme observers');
console.log('MAX dark/light theme, rerender, and session-preservation checks passed');
