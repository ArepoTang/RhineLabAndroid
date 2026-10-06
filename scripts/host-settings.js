/*
 * Rhine Lab terminal — host settings.
 *
 * The APK is its own Wallpaper Engine host. Frame budget, render quality and model
 * precision normally arrive from the host, and the wallpaper build ships no settings
 * surface of its own (the workbench that would have one is dead-code-eliminated), so
 * a phone gets the full-weight defaults and no way out. This file seeds those
 * properties before the app boots and adds a panel to change them.
 *
 * Nothing upstream is duplicated: every change goes through `wallpaperPropertyListener`,
 * the same entry point a real host uses, which makes the app re-apply and persist on its
 * own. The effective quality is read back from the app's `rhine-settings` entry, so named
 * presets are resolved by the app rather than by a second copy of the preset table here.
 *
 * prepare-web.sh copies this next to the built assets and injects the script tag.
 */
(function () {
  "use strict";

  var OUR_KEY = "rhinelab-host"; // frame rate, precision, super mode (not app prefs)
  var APP_KEY = "rhine-settings"; // the app's own prefs, holds the effective `rendering`
  // Cheapest by default: the phone is thermally limited and geometry, not pixels,
  // is what costs. Everything here is meant to be raised from the panel.
  var DEFAULTS = { fps: 60, super: true, precision: "low" };
  var KNOBS = [
    ["scale", "渲染比例 %", [50, 60, 70, 80, 100, 125, 150, 200]],
    ["pixelRatio", "像素密度上限", [1, 1.5, 2, 3]],
    ["antialias", "抗锯齿", ["off", "smaa"]],
    ["shadows", "阴影贴图", [0, 1024, 2048, 4096]],
    ["aoSamples", "环境光遮蔽", [0, 16, 32, 64]],
    ["aoResolution", "AO 分辨率", [0.5, 0.75, 1]],
    ["depthOfField", "景深", [0, 25, 50, 100, 150]],
    ["transmission", "透射分辨率", [0.25, 0.5, 0.75, 1]],
    ["anisotropy", "各向异性过滤", [1, 2, 4, 8, 16]],
  ];
  var PRESETS = [["performance", "性能"], ["original", "原始"], ["high", "高"], ["ultra", "极高"]];
  var PRECISION = [["high", "高"], ["medium", "中"], ["low", "低"]];

  var host = window.rhineWallpaperHost;
  var listener = window.wallpaperPropertyListener;
  if (!host || !listener) return;

  function load(key, fallback) {
    try {
      return JSON.parse(localStorage.getItem(key)) || fallback;
    } catch (error) {
      return fallback;
    }
  }
  function save(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      /* private mode: settings stay in memory for this run */
    }
  }
  function quality() {
    var rendering = load(APP_KEY, {}).rendering;
    return rendering && typeof rendering === "object" ? rendering : {};
  }

  var own = Object.assign({}, DEFAULTS, load(OUR_KEY, {}));

  // ---- seed before the app reads anything ----------------------------------
  var saved = quality();
  var savedKeys = Object.keys(saved).length > 0;
  KNOBS.forEach(function (knob) {
    if (savedKeys) host.properties["quality" + knob[0].toLowerCase()] = { value: saved[knob[0]] };
  });
  // First run has no app prefs yet, so let the app resolve the cheap preset itself.
  host.properties.renderquality = { value: savedKeys ? "custom" : "performance" };
  host.properties.modelprecision = { value: own.precision };
  host.properties.superperformance = { value: !!own.super };
  host.fps = own.fps;

  function push(properties) {
    listener.applyUserProperties(properties);
  }
  function remember() {
    save(OUR_KEY, own);
  }
  function setRendering(key, value) {
    var next = Object.assign({}, quality());
    next[key] = value;
    var properties = { renderquality: { value: "custom" } };
    KNOBS.forEach(function (knob) {
      if (next[knob[0]] !== undefined) properties["quality" + knob[0].toLowerCase()] = { value: next[knob[0]] };
    });
    push(properties);
  }

  // ---- panel ---------------------------------------------------------------
  var PANEL = "position:fixed;inset:0;z-index:2147483000;display:flex;align-items:center;" +
    "justify-content:center;padding:16px;background:rgba(8,10,8,.45);" +
    "font:13px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;color:#393d32";
  var CARD = "width:100%;max-width:600px;max-height:86vh;overflow:auto;background:#e8e5e1;" +
    "border:1px solid #bcb6ac;padding:18px 20px";
  var ROW = "display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:0 0 10px";
  var LABEL = "flex:0 0 116px;color:#626158";
  var CHIP = "font:inherit;padding:5px 10px;background:#fff;border:1px solid #bcb6ac;color:#393d32";
  var CHIP_ON = "font:inherit;padding:5px 10px;background:#353b30;border:1px solid #353b30;color:#f5f1e9";
  var H = "margin:18px 0 10px;font-size:12px;letter-spacing:.14em;color:#626158";

  var overlay = document.createElement("div");
  overlay.id = "host-settings-panel";
  overlay.style.cssText = PANEL + ";display:none";

  function chip(text, on, onClick) {
    var button = document.createElement("button");
    button.type = "button";
    button.textContent = text;
    button.style.cssText = on ? CHIP_ON : CHIP;
    button.addEventListener("click", function () {
      onClick();
      render();
    });
    return button;
  }
  function row(labelText) {
    var line = document.createElement("div");
    line.style.cssText = ROW;
    var label = document.createElement("span");
    label.style.cssText = LABEL;
    label.textContent = labelText;
    line.appendChild(label);
    return line;
  }
  function heading(text) {
    var node = document.createElement("div");
    node.style.cssText = H;
    node.textContent = text;
    return node;
  }

  var status = document.createElement("div");
  status.style.cssText = "margin-bottom:6px;color:#626158";

  function render() {
    var current = quality();
    var scene = document.getElementById("three-scene");
    var applied = scene && scene.dataset.renderQuality ? JSON.parse(scene.dataset.renderQuality) : {};
    status.textContent = "帧率 " + ((scene && scene.dataset.fps) || "?") + " fps" +
      " ・ 实际渲染 " + (applied.width || "?") + "×" + (applied.height || "?") +
      " (×" + (applied.ratio !== undefined ? Number(applied.ratio).toFixed(2) : "?") + ")" +
      (own.super ? " ・ 超性能模式（覆盖下面的画质项）" : "");

    var body = document.createElement("div");
    body.appendChild(status);

    body.appendChild(heading("画质预设　—　点一下即生效，下面是它的拆解"));
    var presets = row("预设");
    PRESETS.forEach(function (entry) {
      var active = !own.super && matchPreset(current) === entry[0];
      presets.appendChild(chip(entry[1], active, function () {
        own.super = false;
        remember();
        push({ superperformance: { value: false }, renderquality: { value: entry[0] } });
      }));
    });
    body.appendChild(presets);

    body.appendChild(heading("省电与几何"));
    var superRow = row("超性能模式");
    superRow.appendChild(chip("开", !!own.super, function () {
      own.super = true;
      remember();
      push({ superperformance: { value: true } });
    }));
    superRow.appendChild(chip("关", !own.super, function () {
      own.super = false;
      remember();
      push({ superperformance: { value: false } });
    }));
    body.appendChild(superRow);

    var precisionRow = row("模型精度");
    PRECISION.forEach(function (entry) {
      precisionRow.appendChild(chip(entry[1], own.precision === entry[0], function () {
        own.precision = entry[0];
        remember();
        push({ modelprecision: { value: entry[0] } });
      }));
    });
    body.appendChild(precisionRow);

    var fpsRow = row("目标帧率");
    [30, 60, 90].forEach(function (value) {
      fpsRow.appendChild(chip(String(value), own.fps === value, function () {
        own.fps = value;
        remember();
        listener.applyGeneralProperties({ fps: value });
      }));
    });
    body.appendChild(fpsRow);

    body.appendChild(heading("精细设置　—　改动即切到自定义"));
    KNOBS.forEach(function (knob) {
      var key = knob[0];
      var line = row(knob[1]);
      knob[2].forEach(function (value) {
        var active = current[key] === value;
        line.appendChild(chip(String(value), active, function () {
          own.super = false;
          remember();
          push({ superperformance: { value: false } });
          setRendering(key, value);
        }));
      });
      body.appendChild(line);
    });

    var footer = document.createElement("div");
    footer.style.cssText = ROW + ";margin:18px 0 0";
    footer.appendChild(chip("恢复最低画质（默认）", false, function () {
      own = Object.assign({}, DEFAULTS);
      save(OUR_KEY, own);
      runtime = null;
      try {
        localStorage.removeItem(APP_KEY);
      } catch (error) {
        /* nothing to clear */
      }
      push({ superperformance: { value: true }, modelprecision: { value: own.precision }, renderquality: { value: "performance" } });
      listener.applyGeneralProperties({ fps: own.fps });
    }));
    footer.appendChild(chip("关闭", false, function () {
      overlay.style.display = "none";
    }));
    body.appendChild(footer);

    overlay.replaceChildren(body);
  }

  function matchPreset(rendering) {
    // Only used to mark the active preset button; the app resolves the real values.
    var known = {
      performance: { scale: 80, pixelRatio: 1, antialias: "off", shadows: 1024, aoSamples: 0, aoResolution: 0.5, depthOfField: 0, transmission: 0.5, anisotropy: 4 },
      original: { scale: 100, pixelRatio: 1.5, antialias: "off", shadows: 2048, aoSamples: 32, aoResolution: 1, depthOfField: 100, transmission: 1, anisotropy: 16 },
      high: { scale: 125, pixelRatio: 2, antialias: "smaa", shadows: 4096, aoSamples: 32, aoResolution: 1, depthOfField: 100, transmission: 1, anisotropy: 16 },
      ultra: { scale: 150, pixelRatio: 2, antialias: "smaa", shadows: 4096, aoSamples: 64, aoResolution: 1, depthOfField: 100, transmission: 1, anisotropy: 16 },
    };
    for (var name in known) {
      var same = true;
      for (var key in known[name]) if (rendering[key] !== known[name][key]) same = false;
      if (same) return name;
    }
    return "custom";
  }

  var runtime;
  function open() {
    overlay.style.display = "flex";
    render();
    if (!runtime) runtime = setInterval(function () {
      if (overlay.style.display === "none") return;
      render();
    }, 1000);
  }

  var launcher = document.createElement("button");
  launcher.type = "button";
  launcher.setAttribute("aria-label", "终端设置");
  launcher.textContent = "◷";
  launcher.style.cssText = "position:fixed;right:10px;bottom:10px;z-index:2147482999;width:44px;height:44px;" +
    "border-radius:50%;border:1px solid #bcb6ac;background:rgba(232,229,225,.9);" +
    "color:#393d32;font:18px/1 ui-monospace,monospace;opacity:.5";
  launcher.addEventListener("click", open);

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && overlay.style.display !== "none") overlay.style.display = "none";
  });
  document.addEventListener("DOMContentLoaded", function () {
    document.body.appendChild(launcher);
    document.body.appendChild(overlay);
  });
})();
