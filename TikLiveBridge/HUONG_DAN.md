# 🔌 TikLive Bridge — Tự live không cần TikFinity

Phần mềm tự viết: **kết nối thẳng TikTok Live → đẩy lệnh vào Minecraft (RCON)**, kèm **dashboard** và **overlay** riêng. Thay thế hoàn toàn TikFinity.

## 1. Chuẩn bị (làm 1 lần)
- Đã bật RCON trong `server.properties` của Server Diamond:
  ```
  enable-rcon=true
  rcon.password=diamond_live_2026
  rcon.port=25575
  ```
- Cài thư viện (tự chạy lần đầu khi mở `start.bat`, hoặc gõ `npm install`).

## 2. Chạy
1. Bật **Minecraft server** trước (`start.bat` của Server Diamond).
2. Bật **TikLive Bridge**: chạy `TikLiveBridge/start.bat` → tự mở dashboard `http://localhost:8088`.
3. Trong dashboard: nhập **username kênh TikTok** (đang live) → bấm **Ket noi**.
4. Dán overlay vào OBS/TikTok Studio: `http://localhost:8088/overlay.html` (nền trong suốt).

## 3. Dashboard có gì
- **Kết nối TikTok**: nhập @username, connect/ngắt; đèn xanh = đang nhận event.
- **Minecraft**: đổi tên nhân vật (`player`), delay giữa các lệnh.
- **Bảng mapping**: thêm/sửa/xóa rule, bật-tắt từng cái, lưu lại.
- **Test nhanh**: bắn thử event (gift/comment/like…) **không cần đang live** để kiểm tra lệnh.
- **Event trực tiếp + Log**: xem real-time.

## 4. Mapping (file `config.json` hoặc sửa trên dashboard)
Mỗi rule:
| Trường | Ý nghĩa |
|--------|---------|
| `event` | `comment` / `gift` / `like` / `follow` / `share` / `subscribe` |
| `match` | lọc theo tên gift hoặc từ khóa comment. `*` = mọi trường hợp. Bỏ trống = không lọc |
| `threshold` | (chỉ `like`) cộng dồn đủ số này mới bắn 1 lần (vd 50 like → 1 lệnh) |
| `command` | lệnh gửi vào MC, **không có dấu `/`** |
| `enabled` | bật/tắt |

**Biến dùng trong command:** `{player}` `{user}` `{nickname}` `{amount}` `{giftName}` `{comment}` `{coins}`

Ví dụ:
```
spawnmob {player} {user} zombie {amount}
diamond {player} {user} + {amount}
giveitem {player} {user} golden_apple 1
skydrop {player} {user}
```

## 5. Lưu ý
- Một event có thể khớp **nhiều rule** (đều bắn). Muốn gift đặc biệt KHÔNG kích hoạt rule `*` mặc định → xóa/tắt rule `gift-default`.
- Gift dạng combo (hoa giữ tay) chỉ tính **khi kết thúc combo** để không đếm trùng.
- Kênh phải **đang live** mới connect được. Test logic thì dùng nút **Test nhanh**.
- Đổi `rcon.password` thì sửa luôn trong `config.json` (mục `minecraft.rconPassword`).
```
```
