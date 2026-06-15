# 🎨 DESIGN BRIEF — TikLive Bridge UI
> File này để **gửi cho một AI chuyên thiết kế giao diện**. Mô tả cần thiết kế cái gì, ràng buộc kỹ thuật, format kết quả. Gồm **Tiếng Việt** (chủ dự án đọc) và **English** (tối ưu cho AI thiết kế).
>
> 👉 Cách dùng: điền ô "Phong cách" ở Phần 1, rồi copy toàn bộ "ENGLISH BRIEF" gửi cho AI thiết kế (v0, Lovable, ChatGPT, Figma AI...). Nhận giao diện ưng ý (HTML/CSS) → gửi lại cho AI lập trình (Claude Code) để ghép vào dự án.
>
> ⚙️ Trạng thái: phần mềm ĐÃ CHẠY THẬT (dashboard + overlay đang hoạt động). Việc của AI thiết kế là **làm ĐẸP LẠI** đúng các thành phần dưới đây, giữ nguyên cấu trúc dữ liệu để ghép lại được.

---

# PHẦN 1 — TIẾNG VIỆT (cho chủ dự án)

## Sản phẩm là gì
Phần mềm nối **TikTok Live → game Minecraft**. Người xem tặng gift / thả tim / bình luận → game phản ứng (cho đồ, spawn quái, hiệu ứng...). Công cụ giúp streamer Minecraft tăng tương tác & kiếm gift. Sau này cho thuê theo tháng.

## Cần thiết kế 2 thứ
1. **DASHBOARD** — bảng điều khiển streamer xem trên máy, KHÔNG lên sóng.
2. **OVERLAY (theo WIDGET)** — LÊN SÓNG, dán vào OBS, nền trong suốt. **Quan trọng:** overlay chia thành nhiều **widget độc lập**, mỗi widget có thể hiện riêng (qua link riêng) hoặc gộp chung. Cần thiết kế từng widget đẹp cả khi đứng riêng lẫn khi đứng chung.

## Phong cách mình muốn
> ✏️ **Chủ dự án điền vào đây trước khi gửi:** thích phong cách gì? (hiện đại tối giản / game thủ neon / Minecraft khối vuông / dễ thương pastel / sang trọng tối màu...). Màu chủ đạo? Có logo chưa? Tông cảm xúc (vui nhộn / chuyên nghiệp / kịch tính)?

---

# PART 2 — ENGLISH BRIEF (give this to the design AI)

## Product
**TikLive Bridge** — software connecting **TikTok Live to a Minecraft game**. When viewers send gifts, likes, comments, follows, or shares during a live stream, the game reacts. Helps Minecraft streamers boost engagement and earn gifts. Will be rented monthly to other streamers (multi-tenant SaaS).

The app already works; we need a **visual redesign** of two surfaces below. **Keep the data shapes and element responsibilities identical** so the result drops back into the running app.

---

## SURFACE 1 — CONTROL DASHBOARD (operator-facing, NOT on stream)
A web control panel on the streamer's second monitor. Priorities: **clarity, fast control, real-time feedback**. Desktop-first (1280px+), shouldn't break narrower.

Sections (all currently exist):
1. **Connection bar:** TikTok @username input, Connect / Disconnect buttons, live status dot (green = receiving events, red = offline), channel name.
2. **Minecraft settings:** target player name, command-delay number. (Secondary.)
3. **Quick Test panel:** fire a fake event — type dropdown (comment/gift/like/follow/share), user field, text/gift-name field, amount field — plus a raw-command input. Lets streamer test offline.
4. **Mapping table (core):** rules mapping event → Minecraft command. Each row: enable toggle, event type, match filter (gift name / comment keyword / "*"), threshold (likes only), command template, note, delete. Add-rule + Save buttons. Show template variables: `{player} {user} {nickname} {amount} {giftName} {comment} {coins}`.
5. **Mapping presets:** a dropdown of ready-made rule sets (e.g. "Help mode", "Troll mode", "Balanced") with an "Apply preset" button and a description line. Applying replaces the table; the streamer can then tweak.
6. **Overlay links:** a list of copyable URLs (full overlay + one per widget) each with a Copy button. (See widget list below.)
7. **Live event feed:** real-time incoming events, icon + username + detail, color-coded by type.
8. **System log:** real-time technical lines (rule fired, command sent, errors).

**Goals:** scannable while the streamer talks; mapping table readable with 20+ rows; status unmistakable.

---

## SURFACE 2 — STREAM OVERLAY (audience-facing, ON stream) — WIDGET-BASED
Rendered as OBS browser source(s) over gameplay. **MUST be fully transparent background, no scrollbars, no page padding.** Big, bold, readable over busy Minecraft visuals. Motion/personality welcome (entertainment).

**Architecture (keep this):** ONE `overlay.html`. A URL query param selects mode:
- `overlay.html` → **full** mode: shows all enabled widgets at once, each in its own screen corner/region (center stays clear for gameplay).
- `overlay.html?widget=NAME` → **solo** mode: shows ONLY that one widget (for embedding individually).

Design **each widget to look good BOTH standalone and combined.** The four widgets:

1. **`events`** (bottom-left): animated toast pop-ups per interaction. e.g. "*Username* sent *Rose ×5* 🌹", "*Username* followed ➕", "*Username*: <comment>". Slide in/out, stack, auto-dismiss (~5s). Distinct accent color + icon per event type (gift, comment, like, follow, share, subscribe).
2. **`stats`** (top-left): three compact counters — Coins, Gifts, Likes — totals for the session.
3. **`topgifters`** (top-right): ranked leaderboard (top 5) of viewers by coins this session — rank badge (gold/silver/bronze), name, coin amount.
4. **`diamond`** (top-center): a GOAL counter — big "current / goal" number (e.g. 47 / 64) with an animated progress bar, a label (e.g. "💎 Diamonds"), and a WIN score below ("🏆 WIN 3 / 10"). Celebrate when the goal is reached or WIN increments (pulse/burst). This is the centerpiece widget.

> Note: this is the "Diamond" game theme. Future game themes will define their own widget sets — so keep each widget visually **self-contained and themeable**, not hard-wired to the others.

**Goals:** instantly readable on stream, eye-catching but never blocking gameplay, celebratory feedback that encourages more gifting.

---

## TECHNICAL CONSTRAINTS (the design must be buildable & re-integratable)
- **Plain HTML + CSS + vanilla JavaScript**, no build step. Self-contained files (inline `<style>`/`<script>`).
- **All colors via CSS variables** at `:root` (`--bg`, `--card`, `--accent`, `--text`...) for easy theming.
- Overlay: `background: transparent`, no scrollbars, no padding. Each widget positioned absolutely in its region; solo mode shows only the requested widget.
- Real-time data arrives via **WebSocket**: `new WebSocket('ws://' + location.host)`. Messages are JSON with a `kind` field. **Mock these with setInterval for the demo.** Exact shapes:
  ```js
  // incoming event (for `events` widget)
  { kind:'event', event:{ type:'gift'|'comment'|'like'|'follow'|'share'|'subscribe',
                          user:'uniqueId', nickname:'Display Name',
                          giftName:'Rose', amount:5, coins:50, comment:'hi' } }
  // session stats (for `stats` + `topgifters`)
  { kind:'stats', totals:{ coins:1125, gifts:26, likes:240 },
                  topGifters:[ { user:'lan_anh', nickname:'Lan Anh', coins:1000, gifts:9 }, ... ] }
  // diamond counter (for `diamond`)
  { kind:'diamond', counter:{ count:47, goal:64, win:3, winGoal:10, label:'Diamonds' } }
  ```
  Dashboard also receives `{kind:'log',...}` and `{kind:'status',connected,username}`.
- Initial state can be fetched once from `GET /api/state` (returns `{connected, config, pack, session:{totals,topGifters}, counter, recentEvents}`).
- **Framework-free** (no React/Vue build). CDN Tailwind acceptable if needed.
- Populate everything with realistic sample data so mockups look complete.

## DELIVERABLE
Return:
- **`dashboard.html`** — the full control panel, all sections above.
- **`overlay.html`** — supporting both full mode and `?widget=events|stats|topgifters|diamond` solo mode, all four widgets styled.

Each file self-contained, runnable in a browser with mock data, transparent overlay background. Prioritize visual quality and clear, themeable component structure. Don't worry about real backend wiring — it will be reconnected after.
