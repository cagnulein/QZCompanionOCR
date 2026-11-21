/*
 * Companion-only adaptation of QZ's /floating/hfloating.htm.
 * Injected after page load; QZ itself and the WebSocket metric stream are untouched.
 */
(function () {
  "use strict";

  if (window.__qzCompanionHorizontalPatched) return;

  var bar = document.getElementById("metrics-bar");
  if (!bar ||
      !Array.isArray(window.availableMetrics) ||
      typeof window.updateHorizontalMetrics !== "function") return;

  window.__qzCompanionHorizontalPatched = true;

  var STORAGE_KEY = "qzcompanion.horizontal.metrics.v1";
  var DEFAULT_METRICS = ["watt", "cadence", "heart", "resistance", "pelotonresistance"];
  var validMetricIds = availableMetrics.map(function (metric) { return metric.id; });

  function filterSelection(ids) {
    if (!Array.isArray(ids)) return [];
    var seen = {};
    return ids.filter(function (id) {
      if (typeof id !== "string" || validMetricIds.indexOf(id) === -1 || seen[id]) return false;
      seen[id] = true;
      return true;
    });
  }

  function readSelection() {
    try {
      var stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored) {
        var parsed = filterSelection(JSON.parse(stored));
        if (parsed.length > 0) return parsed;
      }
    } catch (error) {
      console.warn("Companion: metric preferences unavailable", error);
    }
    return filterSelection(DEFAULT_METRICS);
  }

  var selectedMetrics = readSelection();

  function applySelection() {
    availableMetrics.forEach(function (metric) {
      metricsPreference[metric.id] = selectedMetrics.indexOf(metric.id) !== -1;
    });
  }

  function ensureQuickActions() {
    var metricsBar = document.getElementById("metrics-bar");
    if (!metricsBar || metricsBar.querySelector(".quick-actions")) return;

    var actions = document.createElement("div");
    actions.className = "quick-actions";
    actions.innerHTML =
      '<button class="quick-action-btn" onclick="Start()" title="Start/Pause">▶/⏸</button>' +
      '<button class="quick-action-btn" onclick="Stop()" title="Stop">⏹</button>' +
      '<button class="quick-action-btn autoresistance" onclick="AutoResistance()" title="Auto Resistance">🧲</button>' +
      '<button class="quick-action-btn" onclick="toggleMetricSelector()" title="Select Metrics">⚙</button>' +
      '<button class="quick-action-btn" onclick="toggleCompletePanel()" title="Full Controls">⋯</button>' +
      '<button class="quick-action-btn" onclick="Close()" title="Close Companion">✕</button>';
    metricsBar.appendChild(actions);
  }

  // QZ rebuilds #metrics-bar using innerHTML, which destroys its .quick-actions node.
  // Apply Companion-local choices whenever QZ refreshes the bar (including after getsettings).
  var qzUpdateHorizontalMetrics = window.updateHorizontalMetrics;
  window.updateHorizontalMetrics = function () {
    applySelection();
    var result = qzUpdateHorizontalMetrics.apply(this, arguments);
    ensureQuickActions();
    return result;
  };

  // QZ's saveMetricPreferences updates the global tile_* settings via WebSocket.
  // On Companion, metric choices must stay local and must never change the QZ UI.
  window.saveMetricPreferences = function () {
    var activeItems = document.querySelectorAll("#metric-selector-grid .metric-selector-item.active");
    var chosen = [];
    for (var i = 0; i < activeItems.length; i++) {
      chosen.push(activeItems[i].dataset.metric);
    }
    chosen = filterSelection(chosen);
    // Avoid accidentally hiding the entire metrics bar.
    if (chosen.length) selectedMetrics = chosen;

    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(selectedMetrics));
    } catch (error) {
      console.warn("Companion: could not save metric preferences", error);
    }

    window.updateHorizontalMetrics();

    var selector = document.getElementById("metric-selector");
    if (selector) selector.style.display = "none";
    if (window.Android && typeof Android.restoreFloatingWindow === "function") {
      Android.restoreFloatingWindow();
    }
  };

  var qzClose = window.Close;
  window.Close = function () {
    // Closing the remote QZ window would be surprising when Companion is just an overlay.
    if (window.Android && typeof Android.closeCompanionWindow === "function") {
      Android.closeCompanionWindow();
    } else if (typeof qzClose === "function") {
      qzClose.apply(this, arguments);
    }
  };

  var style = document.createElement("style");
  style.id = "qz-companion-horizontal-style";
  style.textContent =
    ".horizontal-bar { box-sizing: border-box; padding-right: 255px; }" +
    ".horizontal-bar .quick-actions {" +
    "display:flex !important;position:fixed;top:5px;right:5px;z-index:20;" +
    "align-items:center;background:rgba(0,0,0,.85);padding:3px;border-radius:5px;" +
    "}" +
    ".horizontal-bar .metric {min-width:90px;}" +
    ".horizontal-bar .metric-value {font-size:24px;}" +
    "#metric-selector, #controls-panel, #complete-panel {box-sizing:border-box;}";
  document.head.appendChild(style);

  // Initial render is safe even if QZ already ran initializeTemplate().
  window.updateHorizontalMetrics();
})();
