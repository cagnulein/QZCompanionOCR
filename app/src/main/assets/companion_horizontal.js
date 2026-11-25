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
    // Companion's Android settings screen is the source of truth.
    // An unset native preference deliberately falls back to older WebView storage.
    try {
      if (window.Android && typeof Android.getCompanionMetrics === "function") {
        var nativeCsv = Android.getCompanionMetrics();
        if (typeof nativeCsv === "string" && nativeCsv.length) {
          var nativeIds = filterSelection(nativeCsv.split(","));
          if (nativeIds.length) return nativeIds;
        }
      }
    } catch (error) {
      console.warn("Companion: Android metric preferences unavailable", error);
    }
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

  // QZ destroys its original quick-action buttons on each render.
  // Companion intentionally keeps the overlay read-only: Android TV selection
  // is handled by the focusable settings dialog in MainActivity instead.
  bar.addEventListener("click", function (event) {
    event.stopImmediatePropagation();
  }, true);

  window.setCompanionMetricSelection = function (csv) {
    var next = filterSelection(typeof csv === "string" ? csv.split(",") : []);
    if (!next.length) return;
    selectedMetrics = next;
    window.updateHorizontalMetrics();
  };

  // QZ rebuilds #metrics-bar using innerHTML, which destroys its .quick-actions node.
  // Apply Companion-local choices whenever QZ refreshes the bar (including after getsettings).
  var qzUpdateHorizontalMetrics = window.updateHorizontalMetrics;
  window.updateHorizontalMetrics = function () {
    applySelection();
    var result = qzUpdateHorizontalMetrics.apply(this, arguments);
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
    if (window.Android && typeof Android.saveCompanionMetrics === "function") {
      Android.saveCompanionMetrics(selectedMetrics.join(","));
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

  // CSS pixels correspond to Android dp in the WebView. Native Companion
  // calculates the geometry ratio in physical pixels and sends a unitless scale.
  // Never use transform:scale(): it would leave the original layout's text bounds
  // in place and the reduced overlay would still clip the metrics.
  window.setCompanionTextScale = function (value) {
    var scale = Number(value);
    if (!isFinite(scale) || scale <= 0) return;
    scale = Math.max(0.30, Math.min(2.0, scale));
    document.documentElement.style.setProperty("--companion-h-scale", String(scale));
  };

  var style = document.createElement("style");
  style.id = "qz-companion-horizontal-style";
  style.textContent =
    ".horizontal-bar {" +
    "box-sizing:border-box;padding-right:0;" +
    "height:calc(70px * var(--companion-h-scale, 1)) !important;" +
    "}" +
    // Never display overlay controls on top of Peloton during a workout.
    ".horizontal-bar .quick-actions, .horizontal-bar button, " +
    "#controls-panel, #metric-selector, #complete-panel " +
    "{display:none !important;}" +
    ".horizontal-bar .metric {" +
    "min-width:calc(80px * var(--companion-h-scale, 1)) !important;" +
    "padding:calc(5px * var(--companion-h-scale, 1)) !important;" +
    "}" +
    ".horizontal-bar .metric-name {" +
    "font-size:calc(12px * var(--companion-h-scale, 1)) !important;" +
    "margin-bottom:calc(2px * var(--companion-h-scale, 1)) !important;" +
    "}" +
    ".horizontal-bar .metric-value {" +
    "font-size:calc(24px * var(--companion-h-scale, 1)) !important;" +
    "}" +
    ".horizontal-bar .metric-avg {" +
    "font-size:calc(10px * var(--companion-h-scale, 1)) !important;" +
    "}";
  document.head.appendChild(style);

  // Initial render is safe even if QZ already ran initializeTemplate().
  window.updateHorizontalMetrics();
})();
