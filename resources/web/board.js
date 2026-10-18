"use strict";

const title = document.getElementById("title");
const version = document.getElementById("version");
const clock = document.getElementById("clock");
const columns = document.getElementById("columns");
const flaps = document.getElementById("flaps");
const map = document.getElementById("map");
const lights = {
  reservations: document.getElementById("light-reservations"),
  adsb: document.getElementById("light-adsb"),
  weather: document.getElementById("light-weather")
};

let view = null;
let screenIndex = 0;
let screenStarted = Date.now();
let cells = [];
let particles = [];
let particleKey = "";
let fetchTimer = null;
let metarPlate = null;

// Quil sets the text ascent from the ink of "d", which on Skyfont is the
// plate rather than the em square. The letter cutout reaches the bottom of
// that plate, so the line color runs to the plate's bottom edge.
const skyfontPlate = {advance: 38 / 80, top: 4 / 80, height: 56 / 80};

function layoutSize() {
  const width = Math.max(1, window.innerWidth);
  const height = Math.max(1, window.innerHeight);
  const columns = 64;
  const sideInset = 10;
  const charGap = 0.08;
  const lineGap = 0.25;
  const textFraction = 0.87;
  const flapWidth = Math.max(1, (width - sideInset) / columns);
  const fontHeight = flapWidth * (1 - charGap) / skyfontPlate.advance;
  const lineHeight = fontHeight * skyfontPlate.height * (1 + lineGap);
  const rows = Math.max(3, Math.floor((height * textFraction) / lineHeight));
  return {width, height, rows, flapWidth, fontHeight, lineHeight};
}

function rgba(rgb) {
  const r = rgb[0];
  const g = rgb[1];
  const b = rgb[2];
  const a = rgb.length > 3 ? rgb[3] / 255 : 1;
  return "rgba(" + r + "," + g + "," + b + "," + a + ")";
}

function clockText(date) {
  const hh = String(date.getUTCHours()).padStart(2, "0");
  const mm = String(date.getUTCMinutes()).padStart(2, "0");
  const colon = date.getUTCMilliseconds() > 500 ? ":" : " ";
  return hh + colon + mm;
}

function currentEntry() {
  const sequence = view.sequence;
  return sequence[screenIndex % sequence.length];
}

function currentScreen() {
  return view.screens[currentEntry().screen];
}

function nextChar(chars, current) {
  const at = chars.indexOf(current);
  if (at < 0) return " ";
  return chars[(at + 1) % chars.length];
}

function addCell() {
  const el = document.createElement("div");
  el.className = "cell";
  flaps.appendChild(el);
  cells.push({el, char: " ", target: " ", rgb: [30, 30, 30]});
}

function growCells(needed) {
  while (cells.length < needed) addCell();
}

function placeCell(size, line, row, col) {
  const cell = cells[row * view.cols + col];
  cell.target = line.text[col] || " ";
  cell.rgb = line.rgb;
  cell.el.style.left = (col * size.flapWidth) + "px";
  cell.el.style.top = (row * size.lineHeight) + "px";
  cell.el.style.width = size.flapWidth + "px";
  cell.el.style.height = size.lineHeight + "px";
  cell.el.style.fontSize = size.fontHeight + "px";
  cell.el.style.display = "block";
}

function hideExtraCells(needed) {
  cells.forEach((cell, index) => {
    if (index >= needed) cell.el.style.display = "none";
  });
}

function ensureCells(size, lines) {
  const needed = lines.length * view.cols;
  growCells(needed);
  hideExtraCells(needed);
  lines.forEach((line, row) => {
    for (let col = 0; col < view.cols; col++) placeCell(size, line, row, col);
  });
}

function clearFlap(cell) {
  cell.el.textContent = "";
  cell.el.style.background = "none";
}

function backingBox(fontSize) {
  const plate = skyfontPlate;
  return {
    x: fontSize * plate.advance * 0.1,
    y: fontSize * (plate.top + plate.height * 0.1),
    w: fontSize * plate.advance * 0.8,
    h: fontSize * plate.height * 0.9
  };
}

function placeBacking(cell, size) {
  const box = backingBox(size.fontHeight);
  cell.el.style.padding = "0";
  cell.el.style.backgroundColor = "transparent";
  cell.el.style.backgroundImage = "linear-gradient(" + rgba(cell.rgb) + "," + rgba(cell.rgb) + ")";
  cell.el.style.backgroundRepeat = "no-repeat";
  cell.el.style.backgroundPosition = box.x + "px " + box.y + "px";
  cell.el.style.backgroundSize = box.w + "px " + box.h + "px";
}

function paintGlyph(cell, size) {
  cell.el.textContent = cell.char;
  placeBacking(cell, size);
}

function paintCell(cell, size) {
  if (cell.char === " ") return clearFlap(cell);
  paintGlyph(cell, size);
}

function flapSettled(flap, elapsed) {
  return elapsed > flap["duration-ms"];
}

function stillFlapping(cell) {
  return cell.char !== cell.target;
}

function maybeAdvanceChar(flap, cell) {
  if (Math.random() < flap.chance) cell.char = nextChar(flap.chars, cell.char);
}

function stepOneFlap(flap, cell) {
  if (!stillFlapping(cell)) return;
  maybeAdvanceChar(flap, cell);
}

function stepCell(flap, cell) {
  for (let step = 0; step < flap.steps; step++) stepOneFlap(flap, cell);
}

function updateCell(flap, cell, elapsed) {
  if (flapSettled(flap, elapsed)) cell.char = cell.target;
  else stepCell(flap, cell);
}

function visibleCell(cell) {
  return cell.el.style.display !== "none";
}

function stepFlaps(size) {
  const flap = view.flap;
  const elapsed = Date.now() - screenStarted;
  cells.forEach((cell) => {
    if (!visibleCell(cell)) return;
    updateCell(flap, cell, elapsed);
    paintCell(cell, size);
  });
}

function showColumns(size, screen) {
  columns.textContent = "";
  screen.columns.forEach((column) => {
    const label = document.createElement("div");
    label.className = "column-label";
    label.style.left = (column.col * size.flapWidth) + "px";
    label.textContent = column.label;
    columns.appendChild(label);
  });
}

function showLights() {
  ["reservations", "adsb", "weather"].forEach((name) => {
    lights[name].style.background = rgba(view.lights[name]);
  });
}

function resetCells() {
  cells.forEach((cell) => {
    cell.char = " ";
  });
}

function windScreen(entry, screen) {
  return entry.screen === "wind-map" && screen.scene;
}

function showFlaps(size, screen, fresh) {
  if (fresh) resetCells();
  ensureCells(size, screen.lines);
}

function showScreen(size, fresh) {
  const entry = currentEntry();
  const screen = currentScreen();
  document.body.classList.toggle("wind", Boolean(windScreen(entry, screen)));
  title.textContent = screen.header;
  version.textContent = view.version;
  showLights();
  showColumns(size, screen);
  if (windScreen(entry, screen)) return;
  showFlaps(size, screen, fresh);
}

function advance() {
  if (!view) return;
  screenIndex = (screenIndex + 1) % view.sequence.length;
  screenStarted = Date.now();
  showScreen(layoutSize(), true);
}

function screenExpired() {
  const elapsed = (Date.now() - screenStarted) / 1000;
  return elapsed > currentEntry().duration;
}

function dueForAdvance() {
  return view && screenExpired();
}

function maybeAdvance() {
  if (dueForAdvance()) advance();
}

function sampleField(field, x, y) {
  const clamp = (value, lo, hi) => Math.max(lo, Math.min(hi, value));
  const gx = (clamp(x, 0, field.width) / field.width) * (field.cols - 1);
  const gy = (clamp(y, 0, field.height) / field.height) * (field.rows - 1);
  const col0 = Math.floor(gx);
  const row0 = Math.floor(gy);
  const cf = gx - col0;
  const rf = gy - row0;
  const col1 = Math.min(field.cols - 1, col0 + 1);
  const row1 = Math.min(field.rows - 1, row0 + 1);
  const at = (col, row) => field.cells[row][col];
  const mix = (a, b, t) => a + (b - a) * t;
  const tl = at(col0, row0);
  const tr = at(col1, row0);
  const bl = at(col0, row1);
  const br = at(col1, row1);
  return [
    mix(mix(tl[0], tr[0], cf), mix(bl[0], br[0], cf), rf),
    mix(mix(tl[1], tr[1], cf), mix(bl[1], br[1], cf), rf)
  ];
}

function birthParticle(scene, seed, born) {
  const motion = scene.motion;
  const span = motion["max-life"] - motion["min-life"];
  return {
    x: Math.random() * scene.width,
    y: Math.random() * scene.height,
    seed,
    born,
    life: motion["min-life"] + Math.random() * span,
    u: 0,
    v: 0
  };
}

function sameParticles(scene, key) {
  return key === particleKey && particles.length === scene.motion.count;
}

function fillParticles(scene, now) {
  particles = [];
  for (let i = 0; i < scene.motion.count; i++) {
    particles.push(birthParticle(scene, i, now - Math.random() * 1000));
  }
}

function ensureParticles(scene) {
  const key = scene.source.text + ":" + scene.width + "x" + scene.height;
  if (sameParticles(scene, key)) return;
  particleKey = key;
  fillParticles(scene, Date.now());
}

function alive(elapsed, particle) {
  return elapsed >= 0 && elapsed < particle.life;
}

function fadingIn(motion, elapsed) {
  return elapsed < motion["fade-in"];
}

function fadeInOpacity(motion, elapsed) {
  return elapsed / motion["fade-in"];
}

function steadyOpacity(motion, particle, elapsed) {
  const fadeStart = particle.life - motion["fade-out"];
  if (elapsed < fadeStart) return 1;
  return (particle.life - elapsed) / motion["fade-out"];
}

function shownOpacity(motion, particle, elapsed) {
  if (fadingIn(motion, elapsed)) return fadeInOpacity(motion, elapsed);
  return steadyOpacity(motion, particle, elapsed);
}

function particleOpacity(motion, now, particle) {
  const elapsed = now - particle.born;
  if (!alive(elapsed, particle)) return 0;
  return shownOpacity(motion, particle, elapsed);
}

function belowThreshold(speed, threshold) {
  return speed < threshold[0];
}

function nextWindColor(colors, speed, index, threshold) {
  if (belowThreshold(speed, threshold)) return threshold[1];
  return windColorFrom(colors, speed, index + 1);
}

function windColorFrom(colors, speed, index) {
  if (index >= colors.thresholds.length) return colors.strong;
  return nextWindColor(colors, speed, index, colors.thresholds[index]);
}

function windColor(speed, colors) {
  return windColorFrom(colors, speed, 0);
}

function outsideX(scene, x) {
  return x < 0 || x > scene.width;
}

function outsideY(scene, y) {
  return y < 0 || y > scene.height;
}

function offscreen(scene, x, y) {
  return outsideX(scene, x) || outsideY(scene, y);
}

function expired(now, particle) {
  return (now - particle.born) >= particle.life;
}

function keptParticle(particle, x, y, u, v) {
  return {x, y, seed: particle.seed, born: particle.born, life: particle.life, u, v};
}

function nextParticle(scene, particle, now, x, y, u, v) {
  if (expired(now, particle)) return birthParticle(scene, particle.seed, now);
  return keptParticle(particle, x, y, u, v);
}

function placedParticle(scene, particle, now, x, y, u, v) {
  if (offscreen(scene, x, y)) return birthParticle(scene, particle.seed, now);
  return nextParticle(scene, particle, now, x, y, u, v);
}

function stepParticles(scene, now) {
  particles = particles.map((particle) => {
    const [u, v] = sampleField(scene.field, particle.x, particle.y);
    const x = particle.x + u * scene.dx;
    const y = particle.y - v * scene.dy;
    return placedParticle(scene, particle, now, x, y, u, v);
  });
}

function segmentEnd(scene, particle) {
  const speed = Math.hypot(particle.u, particle.v);
  if (speed <= 0) return [particle.x, particle.y];
  const motion = scene.motion;
  const scale = Math.min(scene.width, scene.height) / motion["segment-base"];
  const length = Math.min(
    motion["segment-max"] * scale,
    Math.max(motion["segment-min"] * scale, speed * motion["segment-per-knot"] * scale)
  );
  return [particle.x + (particle.u / speed) * length, particle.y - (particle.v / speed) * length];
}

function drawStates(ctx, scene) {
  ctx.strokeStyle = rgba(scene.outline);
  ctx.lineWidth = 1;
  scene.states.forEach((state) => {
    state.rings.forEach((ring) => {
      ctx.beginPath();
      ring.forEach((point, index) => {
        if (index === 0) ctx.moveTo(point[0], point[1]);
        else ctx.lineTo(point[0], point[1]);
      });
      ctx.stroke();
    });
    ctx.fillStyle = "rgba(130,150,165,0.6)";
    ctx.font = "12px Bahnschrift, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(state.name, state.label[0], state.label[1]);
  });
}

function drawCircle(ctx, scene) {
  const circle = scene.circle;
  ctx.beginPath();
  circle.points.forEach((point, index) => {
    if (index === 0) ctx.moveTo(point[0], point[1]);
    else ctx.lineTo(point[0], point[1]);
  });
  ctx.strokeStyle = rgba(scene["circle-rgb"]);
  ctx.stroke();
  ctx.fillStyle = "white";
  ctx.font = circle.label.size + "px Bahnschrift, sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  ctx.fillText(circle.label.text, circle.label.x, circle.label.y);
}

function drawMarkers(ctx, scene) {
  scene.markers.forEach((marker) => {
    ctx.beginPath();
    ctx.fillStyle = rgba(marker.rgb);
    ctx.strokeStyle = rgba(scene["marker-stroke"]);
    ctx.lineWidth = 2;
    ctx.arc(marker.x, marker.y, 5.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    if (marker.label) {
      ctx.fillStyle = rgba(marker.rgb);
      ctx.font = "12px Bahnschrift, sans-serif";
      ctx.textAlign = "left";
      ctx.textBaseline = "middle";
      ctx.fillText(marker.label, marker.x + 8, marker.y);
    }
  });
}

function scaleAlign(name) {
  if (name === "wind") return "right";
  return "left";
}

function drawBand(ctx, name, band) {
  ctx.fillStyle = rgba(band.rgb);
  ctx.fillRect(band.x, band.y, band.w, band.h);
  ctx.fillStyle = "white";
  ctx.font = "11px Bahnschrift, sans-serif";
  ctx.textAlign = scaleAlign(name);
  ctx.textBaseline = "middle";
  ctx.fillText(band.label, band["label-x"], band["label-y"]);
}

function drawScaleLabels(ctx, scale) {
  const labels = scale.labels || [];
  labels.forEach((label) => {
    ctx.fillStyle = "white";
    ctx.textAlign = "left";
    ctx.fillText(label.text, label.x, label.y);
  });
}

function drawScaleTitle(ctx, name, scale) {
  ctx.textAlign = scaleAlign(name);
  ctx.textBaseline = "bottom";
  ctx.fillText(scale.title.text, scale.title.x, scale.title.y);
}

function drawScale(ctx, scene, name) {
  const scale = scene.scales[name];
  scale.bands.forEach((band) => drawBand(ctx, name, band));
  drawScaleLabels(ctx, scale);
  drawScaleTitle(ctx, name, scale);
}

function drawScales(ctx, scene) {
  ["wind", "ceiling"].forEach((name) => drawScale(ctx, scene, name));
}

function skyfontReady(size, metrics) {
  if (metrics.actualBoundingBoxAscent === undefined) return false;
  return metrics.width <= size * 0.55;
}

function rememberPlate(size, metrics) {
  if (!skyfontReady(size, metrics)) return null;
  metarPlate = {
    size: size,
    advance: metrics.width,
    top: -metrics.actualBoundingBoxAscent,
    height: metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent
  };
  return metarPlate;
}

function readPlate(ctx) {
  const size = 100;
  const previousFont = ctx.font;
  const previousBaseline = ctx.textBaseline;
  ctx.font = size + "px Skyfont, monospace";
  ctx.textBaseline = "top";
  const metrics = ctx.measureText("X");
  ctx.font = previousFont;
  ctx.textBaseline = previousBaseline;
  return rememberPlate(size, metrics);
}

function plateForMetar(ctx) {
  if (metarPlate) return metarPlate;
  return readPlate(ctx);
}

function metarBacking(ctx, fontSize) {
  const plate = plateForMetar(ctx);
  if (!plate) return {x: 0, y: 0, w: 0, h: 0};
  const scale = fontSize / plate.size;
  return {
    x: plate.advance * scale * 0.1,
    y: (plate.top + plate.height * 0.1) * scale,
    w: plate.advance * scale * 0.8,
    h: plate.height * scale * 0.9
  };
}

function paintMetarGlyph(ctx, metar, x, ch) {
  const box = metarBacking(ctx, metar["font-size"]);
  ctx.fillStyle = rgba(metar.rgb);
  ctx.fillRect(x + box.x, metar.y + box.y, box.w, box.h);
  ctx.fillStyle = "black";
  ctx.fillText(ch, x, metar.y);
}

function drawMetarChar(ctx, metar, index) {
  const ch = metar.line[index];
  if (ch === " ") return;
  paintMetarGlyph(ctx, metar, metar.x + index * metar["flap-width"], ch);
}

function drawMetar(ctx, metar) {
  ctx.font = metar["font-size"] + "px Skyfont, monospace";
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  for (let i = 0; i < metar.line.length; i++) drawMetarChar(ctx, metar, i);
}

function drawFleet(ctx, scene) {
  ctx.fillStyle = "white";
  ctx.font = "11px Bahnschrift, sans-serif";
  ctx.textAlign = "left";
  ctx.textBaseline = "middle";
  scene.fleet.forEach((aircraft) => {
    const triangle = aircraft.triangle;
    ctx.beginPath();
    ctx.moveTo(triangle[0][0], triangle[0][1]);
    ctx.lineTo(triangle[1][0], triangle[1][1]);
    ctx.lineTo(triangle[2][0], triangle[2][1]);
    ctx.closePath();
    ctx.fill();
    ctx.fillText(aircraft.label, aircraft.x, aircraft.y);
  });
}

function drawSource(ctx, scene) {
  ctx.fillStyle = "white";
  ctx.font = scene.source.size + "px Bahnschrift, sans-serif";
  ctx.textAlign = "left";
  ctx.textBaseline = "bottom";
  ctx.fillText(scene.source.text, scene.source.x, scene.source.y);
}

function drawStale(ctx, scene) {
  if (!scene.stale) return;
  ctx.fillStyle = "rgb(255,60,60)";
  ctx.font = scene.stale.size + "px Bahnschrift, sans-serif";
  ctx.textAlign = "right";
  ctx.textBaseline = "bottom";
  ctx.fillText(scene.stale.text, scene.stale.x, scene.stale.y);
}

function drawParticles(ctx, scene, now) {
  const motion = scene.motion;
  particles.forEach((particle) => {
    const speed = Math.hypot(particle.u, particle.v);
    const color = windColor(speed, scene.colors);
    const alpha = (color[3] / 255) * particleOpacity(motion, now, particle);
    const [x2, y2] = segmentEnd(scene, particle);
    ctx.strokeStyle = "rgba(" + color[0] + "," + color[1] + "," + color[2] + "," + alpha + ")";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(particle.x, particle.y);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  });
}

function drawMap(scene) {
  if (!scene) return;
  map.width = scene.width;
  map.height = scene.height;
  const ctx = map.getContext("2d");
  ctx.clearRect(0, 0, scene.width, scene.height);
  ctx.fillStyle = rgba(scene.background);
  ctx.fillRect(0, 0, scene.width, scene.height);
  drawStates(ctx, scene);
  scene.ceilings.forEach((cell) => {
    ctx.fillStyle = rgba(cell.rgb);
    ctx.fillRect(cell.x, cell.y, cell.w, cell.h);
  });
  drawCircle(ctx, scene);
  drawMarkers(ctx, scene);
  drawScales(ctx, scene);
  drawSource(ctx, scene);
  drawMetar(ctx, scene.metar);
  drawStale(ctx, scene);
  ensureParticles(scene);
  const now = Date.now();
  stepParticles(scene, now);
  drawParticles(ctx, scene, now);
  drawFleet(ctx, scene);
}

function beginView(hadView) {
  if (!hadView) screenStarted = Date.now();
}

async function refresh() {
  const size = layoutSize();
  const response = await fetch("/board?rows=" + size.rows + "&width=" + size.width + "&height=" + size.height);
  if (!response.ok) return;
  const next = await response.json();
  const hadView = Boolean(view);
  view = next;
  beginView(hadView);
  showScreen(size, !hadView);
}

function showingWind() {
  return document.body.classList.contains("wind") && currentScreen().scene;
}

function drawCurrent(size) {
  if (showingWind()) drawMap(currentScreen().scene);
  else stepFlaps(size);
}

function tick() {
  const size = layoutSize();
  clock.textContent = clockText(new Date());
  if (!view) return;
  maybeAdvance();
  drawCurrent(size);
}

window.addEventListener("keydown", (event) => {
  if (event.key === " ") event.preventDefault();
});

window.addEventListener("keyup", (event) => {
  if (event.key === " ") advance();
  if (event.key === "p" || event.key === "P") {
    fetch("/command", {method: "POST", headers: {"Content-Type": "application/edn"}, body: '{:key "p"}'});
  }
});

window.addEventListener("resize", () => {
  clearTimeout(fetchTimer);
  fetchTimer = setTimeout(refresh, 200);
});

setInterval(tick, 33);
setInterval(refresh, 2000);
refresh();
