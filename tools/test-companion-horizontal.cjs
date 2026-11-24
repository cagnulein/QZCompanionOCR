// No external dependencies: test the Companion injection against QZ's DOM contract.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const patch = fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets/companion_horizontal.js'), 'utf8');

const metrics = [
  'speed', 'inclination', 'pace', 'elevation', 'cadence', 'heart', 'watt',
  'powerzone', 'resistance', 'pelotonresistance', 'calories', 'jouls',
  'distance', 'elapsed', 'rowremainingtime', 'pelotonoffset', 'gears', 'nextrow'
];

function makeFixture(sharedStorage = new Map(), opts = {}) {
  const state = {
    nativeMetrics: opts.nativeMetrics || '',
    nativeWrites: [],
    qzSettingsWrites: 0,
    qzCloseCalls: 0,
    localCloseCalls: 0,
    restoreCalls: 0,
    renders: [],
    styles: [],
    activeMetrics: [],
    clickHandler: null,
    selectors: { 'metric-selector': { style: { display: 'block' } } }
  };
  let markup = '';
  const bar = {
    children: [],
    get innerHTML() { return markup; },
    set innerHTML(next) {
      markup = next;
      this.children = [];  // QZ replaces all previous quick actions.
    },
    querySelector(selector) {
      return selector === '.quick-actions'
        ? this.children.find(node => node.className === 'quick-actions') || null : null;
    },
    appendChild(node) { this.children.push(node); },
    addEventListener(name, callback, capture) {
      if (name === 'click') state.clickHandler = {callback, capture};
    }
  };
  state.selectors['metrics-bar'] = bar;
  const localStorage = {
    getItem(key) { return sharedStorage.has(key) ? sharedStorage.get(key) : null; },
    setItem(key, value) { sharedStorage.set(key, value); }
  };
  const context = {
    availableMetrics: metrics.map(id => ({id})),
    metricsPreference: Object.fromEntries(metrics.map(id => [id, true])),
    localStorage,
    console: { warn() {} },
    document: {
      getElementById(id) { return state.selectors[id] || null; },
      createElement(tag) { return {tagName: tag, className: '', innerHTML: '', textContent: ''}; },
      querySelectorAll(selector) {
        assert.equal(selector, '#metric-selector-grid .metric-selector-item.active');
        return state.activeMetrics.map(id => ({dataset: {metric: id}}));
      },
      head: { appendChild(el) { state.styles.push(el); } }
    },
    updateHorizontalMetrics() {
      bar.innerHTML = '';
      state.renders.push({...context.metricsPreference});
    },
    saveMetricPreferences() { state.qzSettingsWrites++; },
    Close() { state.qzCloseCalls++; }
  };
  if (opts.bridge !== false) {
    context.Android = {
      getCompanionMetrics() { return state.nativeMetrics; },
      saveCompanionMetrics(csv) {
        state.nativeMetrics = csv;
        state.nativeWrites.push(csv);
      },
      restoreFloatingWindow() { state.restoreCalls++; },
      closeCompanionWindow() { state.localCloseCalls++; }
    };
  }
  context.window = context;
  vm.runInNewContext(patch, context, {filename: 'companion_horizontal.js'});
  return {state, context, bar, sharedStorage};
}

test('starts with five bicycle metrics and no persistent overlay controls', () => {
  const {state, bar} = makeFixture();
  assert.deepEqual(
    metrics.filter(id => state.renders.at(-1)[id]),
    ['cadence', 'heart', 'watt', 'resistance', 'pelotonresistance']
  );
  assert.equal(bar.querySelector('.quick-actions'), null);
  assert.equal(bar.children.length, 0);
  assert.match(state.styles[0].textContent, /quick-actions/);
  assert.match(state.styles[0].textContent, /display:none !important/);
  assert.match(state.styles[0].textContent, /#controls-panel/);
});

test('QZ metric refresh never reinstates action buttons or changes selected metrics', () => {
  const {context, state, bar} = makeFixture();
  for (let i = 0; i < 4; i++) {
    for (const id of metrics) context.metricsPreference[id] = true;
    context.updateHorizontalMetrics();
    assert.equal(bar.children.length, 0);
    assert.equal(state.renders.at(-1).speed, false);
    assert.equal(state.renders.at(-1).watt, true);
  }
  assert.equal(state.qzSettingsWrites, 0);
});

test('tapping the read-only horizontal bar cannot toggle QZ controls', () => {
  const {state} = makeFixture();
  assert.equal(state.clickHandler.capture, true);
  let blocked = 0;
  state.clickHandler.callback({stopImmediatePropagation() {blocked++;}});
  assert.equal(blocked, 1);
});

test('native TV settings override previous WebView localStorage preferences', () => {
  const storage = new Map([
    ['qzcompanion.horizontal.metrics.v1', JSON.stringify(['heart'])]
  ]);
  const {state} = makeFixture(storage, {nativeMetrics: 'speed,calories'});
  assert.equal(state.renders.at(-1).speed, true);
  assert.equal(state.renders.at(-1).calories, true);
  assert.equal(state.renders.at(-1).heart, false);
});

test('Android selection change updates the running overlay without a QZ settings write', () => {
  const {context, state} = makeFixture();
  context.setCompanionMetricSelection('speed,calories,watts-unknown');
  assert.equal(state.renders.at(-1).speed, true);
  assert.equal(state.renders.at(-1).calories, true);
  assert.equal(state.renders.at(-1).watt, false);
  assert.equal(state.qzSettingsWrites, 0);
  context.setCompanionMetricSelection('');
  assert.equal(state.renders.at(-1).speed, true);
});

test('legacy WebView selection saves to localStorage and native Companion, never QZ', () => {
  const {state, context, sharedStorage} = makeFixture();
  state.activeMetrics = ['watt', 'cadence', 'watt', 'unknown'];
  context.saveMetricPreferences();
  assert.deepEqual(JSON.parse(sharedStorage.get('qzcompanion.horizontal.metrics.v1')),
      ['watt', 'cadence']);
  assert.deepEqual(state.nativeWrites, ['watt,cadence']);
  assert.equal(state.qzSettingsWrites, 0);
  assert.equal(state.selectors['metric-selector'].style.display, 'none');
  assert.equal(state.restoreCalls, 1);
  assert.equal(state.renders.at(-1).cadence, true);
  assert.equal(state.renders.at(-1).heart, false);
});

test('selected metrics persist when WebView reloads and reads Android preferences', () => {
  const storage = new Map();
  const first = makeFixture(storage);
  first.state.activeMetrics = ['heart', 'watt'];
  first.context.saveMetricPreferences();
  const second = makeFixture(storage, {nativeMetrics: first.state.nativeMetrics});
  assert.equal(second.state.renders.at(-1).heart, true);
  assert.equal(second.state.renders.at(-1).watt, true);
  assert.equal(second.state.renders.at(-1).cadence, false);
});

test('prior beta WebView storage remains readable if Android selection has not been set', () => {
  const storage = new Map([
    ['qzcompanion.horizontal.metrics.v1', JSON.stringify(['heart', 'watt'])]
  ]);
  const {state} = makeFixture(storage);
  assert.equal(state.renders.at(-1).heart, true);
  assert.equal(state.renders.at(-1).cadence, false);
});

test('invalid stored data and deselect-all do not leave the bar empty', () => {
  const storage = new Map([['qzcompanion.horizontal.metrics.v1', '{bad json']]);
  const {context, state} = makeFixture(storage);
  state.activeMetrics = [];
  context.saveMetricPreferences();
  assert.equal(state.renders.at(-1).watt, true);
  assert.equal(state.renders.at(-1).heart, true);
  assert.equal(state.qzSettingsWrites, 0);
});

test('close action closes Companion locally and not the QZ floating page', () => {
  const {context, state} = makeFixture();
  context.Close();
  assert.equal(state.localCloseCalls, 1);
  assert.equal(state.qzCloseCalls, 0);
});

test('without Android bridge, close retains the QZ HTML fallback', () => {
  const {context, state} = makeFixture(new Map(), {bridge: false});
  context.Close();
  assert.equal(state.qzCloseCalls, 1);
});

test('reinjection is idempotent and installs only one CSS patch', () => {
  const {context, state, bar} = makeFixture();
  vm.runInNewContext(patch, context, {filename: 'companion_horizontal.js'});
  context.updateHorizontalMetrics();
  assert.equal(state.styles.length, 1);
  assert.equal(bar.children.length, 0);
});
