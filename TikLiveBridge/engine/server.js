// ============================================================
//  TikLive Bridge - Server Diamond
//  TikTok Live  ->  (mapping)  ->  Minecraft (RCON)
//  Thay the TikFinity. Dashboard + Overlay rieng.
// ============================================================

import express from "express";
import { WebSocketServer } from "ws";
import { Rcon } from "rcon-client";
import { readFileSync, writeFileSync, readdirSync } from "fs";
import { fileURLToPath } from "url";
import { dirname, join } from "path";
import http from "http";
import * as TikTok from "tiktok-live-connector";

const __dirname = dirname(fileURLToPath(import.meta.url)); // .../engine
const BASE = join(__dirname, ".."); // .../TikLiveBridge
const CONFIG_PATH = join(BASE, "config.json"); // cai dat chung
const PACKS_DIR = join(BASE, "packs"); // kho goi
const WEB_PORT = 8088; // dashboard + overlay

// ---- Tuong thich nhieu phien ban tiktok-live-connector ----
// v2: TikTokLiveConnection ; v1: WebcastPushConnection
const TikTokConnection =
  TikTok.TikTokLiveConnection || TikTok.WebcastPushConnection;

// ------------------------------------------------------------
// State
// ------------------------------------------------------------
let config = loadConfig(); // cai dat chung (activePack, minecraft, tiktok)
let pack = loadPack(); // goi dang chay (meta, rules, overlay)
let tiktok = null;
let connected = false;
let likeAccumulator = {}; // ruleId -> dem like don
const wsClients = new Set();
const recentEvents = [];

// Bo dem (kim cuong/WIN...) - du lieu do plugin trong game gui sang
let counter = { count: 0, goal: 64, win: 0, winGoal: 0, label: "Kim cuong" };

// Thong ke phien live (reset moi buoi)
let session = newSession();
function newSession() {
  return {
    startedAt: Date.now(),
    totals: { gifts: 0, coins: 0, likes: 0, comments: 0, follows: 0, shares: 0 },
    gifters: {}, // uniqueId -> { nickname, coins, gifts }
  };
}
function topGifters(n = 5) {
  return Object.entries(session.gifters)
    .map(([user, v]) => ({ user, ...v }))
    .sort((a, b) => b.coins - a.coins || b.gifts - a.gifts)
    .slice(0, n);
}
function updateStats(type, d) {
  const t = session.totals;
  if (type === "gift") {
    t.gifts += d.amount || 1;
    t.coins += d.coins || 0;
    const u = d.user || "viewer";
    const g = (session.gifters[u] = session.gifters[u] || {
      nickname: d.nickname || u,
      coins: 0,
      gifts: 0,
    });
    g.nickname = d.nickname || g.nickname;
    g.coins += d.coins || 0;
    g.gifts += d.amount || 1;
  } else if (type === "like") t.likes += d.amount || 1;
  else if (type === "comment") t.comments += 1;
  else if (type === "follow") t.follows += 1;
  else if (type === "share") t.shares += 1;
  broadcast({ kind: "stats", totals: t, topGifters: topGifters() });
}

function loadConfig() {
  return JSON.parse(readFileSync(CONFIG_PATH, "utf-8"));
}
function saveConfig() {
  writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), "utf-8");
}

// ---- Goi (pack): moi server/che do la 1 thu muc trong packs/ ----
function packDir(id = config.activePack) {
  return join(PACKS_DIR, id);
}
function readJsonSafe(path, fallback) {
  try {
    return JSON.parse(readFileSync(path, "utf-8"));
  } catch {
    return fallback;
  }
}
function loadPack(id = config.activePack) {
  const dir = packDir(id);
  const meta = JSON.parse(readFileSync(join(dir, "pack.json"), "utf-8"));
  const mappings = JSON.parse(readFileSync(join(dir, "mappings.json"), "utf-8"));
  const overlay = JSON.parse(readFileSync(join(dir, "overlay.json"), "utf-8"));
  const presets = readJsonSafe(join(dir, "presets.json"), { presets: [] }).presets;
  return { meta, rules: mappings.rules || [], overlay, presets };
}
function savePackMappings() {
  const dir = packDir();
  writeFileSync(
    join(dir, "mappings.json"),
    JSON.stringify({ rules: pack.rules }, null, 2),
    "utf-8"
  );
}
function listPacks() {
  return readdirSync(PACKS_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => {
      try {
        return JSON.parse(readFileSync(join(PACKS_DIR, d.name, "pack.json"), "utf-8"));
      } catch {
        return { id: d.name, name: d.name };
      }
    });
}

// ------------------------------------------------------------
// RCON - gui lenh vao Minecraft (co hang doi)
// ------------------------------------------------------------
const cmdQueue = [];
let queueRunning = false;

async function enqueueCommand(cmd) {
  cmdQueue.push(cmd);
  if (!queueRunning) runQueue();
}

async function runQueue() {
  queueRunning = true;
  while (cmdQueue.length) {
    const cmd = cmdQueue.shift();
    await sendRcon(cmd);
    await sleep(config.minecraft.commandDelayMs || 200);
  }
  queueRunning = false;
}

async function sendRcon(cmd) {
  const { host, rconPort, rconPassword } = config.minecraft;
  try {
    const rcon = await Rcon.connect({
      host,
      port: rconPort,
      password: rconPassword,
    });
    const res = await rcon.send(cmd);
    await rcon.end();
    log("rcon", `> ${cmd}`, res?.trim?.() || "");
    return true;
  } catch (e) {
    log("error", `RCON loi: ${e.message} (lenh: ${cmd})`);
    return false;
  }
}

// ------------------------------------------------------------
// Mapping: event -> lenh
// ------------------------------------------------------------
function fillTemplate(tpl, vars) {
  return tpl.replace(/\{(\w+)\}/g, (_, k) => (vars[k] ?? "").toString());
}

function handleEvent(type, data) {
  // luu de hien tren dashboard/overlay
  pushEvent({ type, ...data, time: Date.now() });
  updateStats(type, data);

  const player = config.minecraft.targetPlayer;
  const user = (data.user || "viewer").replace(/\s+/g, "");

  for (const rule of pack.rules) {
    if (!rule.enabled || rule.event !== type) continue;

    // loc theo "match" (gift name / tu khoa comment); "*" = tat ca
    if (rule.match && rule.match !== "*") {
      const hay =
        type === "comment" ? (data.comment || "") : (data.giftName || "");
      if (!hay.toLowerCase().includes(rule.match.toLowerCase())) continue;
    }

    // like: cong don toi nguong moi ban lenh
    if (type === "like" && rule.threshold) {
      likeAccumulator[rule.id] =
        (likeAccumulator[rule.id] || 0) + (data.amount || 1);
      let fired = 0;
      while (likeAccumulator[rule.id] >= rule.threshold) {
        likeAccumulator[rule.id] -= rule.threshold;
        fired++;
      }
      for (let i = 0; i < fired; i++) {
        fireRule(rule, { player, user, ...data });
      }
      continue;
    }

    fireRule(rule, { player, user, ...data });
  }
}

function fireRule(rule, vars) {
  const cmd = fillTemplate(rule.command, vars);
  log("rule", `[${rule.id}] -> /${cmd}`);
  enqueueCommand(cmd);
}

// ------------------------------------------------------------
// Ket noi TikTok Live
// ------------------------------------------------------------
async function connectTikTok(username) {
  if (connected) await disconnectTikTok();
  config.tiktok.username = username;
  saveConfig();

  tiktok = new TikTokConnection(username);

  // v1 dung connection.connect(); v2 cung co connect()
  bindTikTokEvents(tiktok);

  try {
    const state = await tiktok.connect();
    connected = true;
    log("info", `Da ket noi TikTok live: @${username} (room ${state.roomId || "?"})`);
    broadcast({ kind: "status", connected: true, username });
  } catch (e) {
    connected = false;
    log("error", `Khong ket noi duoc @${username}: ${e.message}`);
    broadcast({ kind: "status", connected: false, error: e.message });
    throw e;
  }
}

async function disconnectTikTok() {
  try {
    if (tiktok) tiktok.disconnect();
  } catch {}
  connected = false;
  likeAccumulator = {};
  broadcast({ kind: "status", connected: false });
  log("info", "Da ngat ket noi TikTok.");
}

function bindTikTokEvents(conn) {
  const on = (name, fn) => {
    try { conn.on(name, fn); } catch {}
  };

  // v2: data la proto, user long trong d.user
  const uid = (d) => d?.user?.uniqueId || d?.user?.nickname || "viewer";
  const nick = (d) => d?.user?.nickname || d?.user?.uniqueId || "viewer";

  on("chat", (d) =>
    handleEvent("comment", {
      user: uid(d),
      nickname: nick(d),
      comment: d.comment || "",
    })
  );
  on("gift", (d) => {
    // gift streak (giftType=1): chi tinh khi combo ket thuc (repeatEnd=1)
    const g = d.giftDetails || {};
    const streaking = g.giftType === 1 && !d.repeatEnd;
    if (streaking) return;
    handleEvent("gift", {
      user: uid(d),
      nickname: nick(d),
      giftName: g.giftName || "Gift",
      amount: d.repeatCount || 1,
      coins: (g.diamondCount || 0) * (d.repeatCount || 1),
    });
  });
  on("like", (d) =>
    handleEvent("like", {
      user: uid(d),
      nickname: nick(d),
      amount: d.likeCount || 1,
    })
  );
  on("follow", (d) =>
    handleEvent("follow", { user: uid(d), nickname: nick(d) })
  );
  on("share", (d) =>
    handleEvent("share", { user: uid(d), nickname: nick(d) })
  );
  on("subscribe", (d) =>
    handleEvent("subscribe", { user: uid(d), nickname: nick(d) })
  );

  on("disconnected", () => {
    connected = false;
    broadcast({ kind: "status", connected: false });
    log("info", "TikTok ngat ket noi.");
  });
  on("streamEnd", () => {
    connected = false;
    broadcast({ kind: "status", connected: false });
    log("info", "Live da ket thuc.");
  });
}

// ------------------------------------------------------------
// Log + broadcast WebSocket
// ------------------------------------------------------------
function log(level, ...msg) {
  const line = msg.join(" ");
  const stamp = new Date().toLocaleTimeString();
  console.log(`[${stamp}] [${level}] ${line}`);
  broadcast({ kind: "log", level, line, time: Date.now() });
}

function pushEvent(ev) {
  recentEvents.unshift(ev);
  if (recentEvents.length > 50) recentEvents.pop();
  broadcast({ kind: "event", event: ev });
}

function broadcast(obj) {
  const msg = JSON.stringify(obj);
  for (const ws of wsClients) {
    if (ws.readyState === 1) ws.send(msg);
  }
}

// ------------------------------------------------------------
// Web server: dashboard + overlay + API
// ------------------------------------------------------------
const app = express();
app.use(express.json());
app.use(express.static(join(__dirname, "public")));

app.get("/api/state", (req, res) => {
  res.json({
    connected,
    config,
    pack,
    packs: listPacks(),
    recentEvents,
    session: { totals: session.totals, topGifters: topGifters() },
    counter,
  });
});

// Plugin trong game gui so kim cuong/WIN sang day
app.post("/api/diamond", (req, res) => {
  counter = { ...counter, ...req.body };
  broadcast({ kind: "diamond", counter });
  res.json({ ok: true });
});

// Reset thong ke phien (dau buoi live moi)
app.post("/api/session/reset", (req, res) => {
  session = newSession();
  broadcast({ kind: "stats", totals: session.totals, topGifters: [] });
  log("info", "Da reset thong ke phien.");
  res.json({ ok: true });
});

app.post("/api/connect", async (req, res) => {
  const username = (req.body.username || config.tiktok.username || "").trim().replace(/^@/, "");
  if (!username) return res.status(400).json({ error: "Thieu username" });
  try {
    await connectTikTok(username);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post("/api/disconnect", async (req, res) => {
  await disconnectTikTok();
  res.json({ ok: true });
});

// Luu cai dat chung (minecraft, tiktok)
app.post("/api/config", (req, res) => {
  config = { ...config, ...req.body };
  saveConfig();
  log("info", "Da luu cai dat chung.");
  res.json({ ok: true, config });
});

// Luu mapping cua goi dang chay
app.post("/api/mappings", (req, res) => {
  if (Array.isArray(req.body.rules)) {
    pack.rules = req.body.rules;
    savePackMappings();
    log("info", `Da luu mapping goi [${config.activePack}].`);
  }
  res.json({ ok: true, rules: pack.rules });
});

// Ap dung 1 mapping mau (preset) vao goi dang chay - khach co the chinh tiep sau
app.post("/api/preset", (req, res) => {
  const id = (req.body.id || "").trim();
  const p = (pack.presets || []).find((x) => x.id === id);
  if (!p) return res.status(404).json({ error: `Khong co mau '${id}'` });
  pack.rules = JSON.parse(JSON.stringify(p.rules)); // copy de chinh duoc
  savePackMappings();
  log("info", `Da ap dung mau [${p.name}] (${pack.rules.length} rule).`);
  broadcast({ kind: "pack", pack });
  res.json({ ok: true, rules: pack.rules });
});

// Doi goi dang chay
app.post("/api/pack", (req, res) => {
  const id = (req.body.id || "").trim();
  try {
    const next = loadPack(id);
    config.activePack = id;
    saveConfig();
    pack = next;
    likeAccumulator = {};
    log("info", `Da chuyen sang goi [${id}] - ${pack.meta.name}.`);
    broadcast({ kind: "pack", pack });
    res.json({ ok: true, pack });
  } catch (e) {
    res.status(400).json({ error: `Khong nap duoc goi '${id}': ${e.message}` });
  }
});

// test 1 event tu dashboard (khong can dang live)
app.post("/api/test", (req, res) => {
  const { type, user, comment, giftName, amount, coins } = req.body;
  handleEvent(type, {
    user: user || "tester",
    nickname: user || "tester",
    comment,
    giftName,
    amount: amount || 1,
    coins: coins || 0,
  });
  res.json({ ok: true });
});

// gui lenh tho truc tiep (debug)
app.post("/api/raw", async (req, res) => {
  const cmd = (req.body.command || "").replace(/^\//, "");
  if (!cmd) return res.status(400).json({ error: "Thieu lenh" });
  await enqueueCommand(cmd);
  res.json({ ok: true });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });
wss.on("connection", (ws) => {
  wsClients.add(ws);
  ws.send(JSON.stringify({ kind: "status", connected, username: config.tiktok.username }));
  ws.on("close", () => wsClients.delete(ws));
});

server.listen(WEB_PORT, () => {
  log("info", `Dashboard:  http://localhost:${WEB_PORT}/`);
  log("info", `Overlay:    http://localhost:${WEB_PORT}/overlay.html`);
});

// ------------------------------------------------------------
function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}
