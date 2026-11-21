// No external dependencies: exercise the injected patch against a mocked QZ DOM.
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

function makeFixture(sharedStorage = new Map(), withBridge = true) {
  const state = {
    qzSettingsWrites: 0,
    qzCloseCalls: 0,
    localCloseCalls: 0,
    restoreCalls: 0,
    renders: [],
    styles: [],
    activeMetrics: [],
    selectors: { 'metric-selector': { style: { display: 'block' } } }
  };
  let markup = '';
  const bar = {
    children: [],
    get innerHTML() { return markup; },
    set innerHTML(next) {
      markup = next;
      this.children = [];
    },
    querySelector(selector) {
      return selector === '.quick-actions'
        ? this.children.find(node => node.className === 'quick-actions') || null : null;
    },
    appendChild(node) { this.children.push(node); }
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
      // Simulates the QZ implementation (innerHTML clears .quick-actions).
      bar.innerHTML = '';
      state.renders.push({...context.metricsPreference});
    },
    saveMetricPreferences() {
      // Calling the QZ original would send tile_* updates to the iPad!
      state.qzSettingsWrites++;
    },
    Close() { state.qzCloseCalls++; }
  };
  if (withBridge) {
    context.Android = {
      restoreFloatingWindow() { state.restoreCalls++; },
      closeCompanionWindow() { state.localCloseCalls++; }
    };
  }
  context.window = context;
  vm.runInNewContext(patch, context, {filename: 'companion_horizontal.js'});
  return {state, context, bar, sharedStorage};
}

test('defaults to five useful EX3 metrics independently from QZ settings', () => {
  const {state, bar} = makeFixture();
  assert.deepEqual(
    metrics.filter(id => state.renders.at(-1)[id]),
    ['cadence', 'heart', 'watt', 'resistance', 'pelotonresistance']
  );
  assert.ok(bar.querySelector('.quick-actions'));
  assert.match(bar.querySelector('.quick-actions').innerHTML, /toggleMetricSelector/);
  assert.match(bar.querySelector('.quick-actions').innerHTML, /Close Companion/);
  assert.equal(state.styles.length, 1);
});

test('quick actions survive every QZ metrics rerender including getsettings callback', () => {
  const {context, state, bar} = makeFixture();
  for (let i = 0; i < 4; i++) {
    // QZ overwrites the preferences with the iPad tile_* settings.
    for (const id of metrics) context.metricsPreference[id] = true;
    context.updateHorizontalMetrics();
    assert.equal(bar.children.length, 1);
    assert.ok(bar.querySelector('.quick-actions'));
    assert.equal(state.renders.at(-1).speed, false);
    assert.equal(state.renders.at(-1).watt, true);
  }
});

test('selector saves only to Companion WebView storage, never to QZ tile_* settings', () => {
  const {state, context, sharedStorage} = makeFixture();
  state.activeMetrics = ['watt', 'cadence', 'watt', 'not-a-real-metric'];
  context.saveMetricPreferences();
  assert.deepEqual(JSON.parse(sharedStorage.get('qzcompanion.horizontal.metrics.v1')), ['watt', 'cadence']);
  assert.equal(state.qzSettingsWrites, 0);
  assert.equal(state.selectors['metric-selector'].style.display, 'none');
  assert.equal(state.restoreCalls, 1);
  assert.equal(state.renders.at(-1).cadence, true);
  assert.equal(state.renders.at(-1).heart, false);
  context.metricsPreference.heart = true;
  context.updateHorizontalMetrics();
  assert.equal(state.renders.at(-1).heart, false);
});

test('per-device local metrics persist when the WebView reloads', () => {
  const storage = new Map();
  const first = makeFixture(storage);
  first.state.activeMetrics = ['heart', 'watt'];
  first.context.saveMetricPreferences();
  const second = makeFixture(storage);
  assert.equal(second.state.renders.at(-1).heart, true);
  assert.equal(second.state.renders.at(-1).watt, true);
  assert.equal(second.state.renders.at(-1).cadence, false);
});

test('invalid storage and deselecting all metrics cannot make the overlay empty', () => {
  const storage = new Map([['qzcompanion.horizontal.metrics.v1', '{invalid json']]);
  const {context, state} = makeFixture(storage);
  state.activeMetrics = [];
  context.saveMetricPreferences();
  assert.equal(state.renders.at(-1).watt, true);
  assert.equal(state.renders.at(-1).heart, true);
  assert.equal(state.qzSettingsWrites, 0);
});

test('close hides Companion locally rather than sending floating_close to QZ', () => {
  const {context, state} = makeFixture();
  context.Close();
  assert.equal(state.localCloseCalls, 1);
  assert.equal(state.qzCloseCalls, 0);
});

test('close can fall back to QZ on a host without the Android bridge', () => {
  const {context, state} = makeFixture(new Map(), false);
  context.Close();
  assert.equal(state.qzCloseCalls, 1);
});

test('reinjecting the same script does not install duplicate wrappers or CSS', () => {
  const {context, state, bar} = makeFixture();
  vm.runInNewContext(patch, context, {filename: 'companion_horizontal.js'});
  context.updateHorizontalMetrics();
  assert.equal(state.styles.length, 1);
  assert.equal(bar.children.length, 1);
});
