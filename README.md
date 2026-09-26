# Kayji-DiscordStatus

Plugin Minecraft (Spigot) gửi **embed trạng thái máy chủ** lên Discord: tự động thông báo ONLINE khi server khởi động và OFFLINE khi tắt, cập nhật ngay trên chính tin nhắn cũ thay vì spam kênh.

> **Tác giả:** Kayji_Tizi · **Phiên bản:** 2.2.0 · **API:** 1.16.5+ · **Java:** 17

## Tính năng

- **Embed trạng thái** với màu riêng cho ONLINE (`#00ff75`) và OFFLINE (`#ff5555`), có title, footer, fields tùy chỉnh.
- **Nhiều bot cùng lúc**: cấu hình danh sách `bots` — mỗi bot có token riêng, presence riêng và nhiều kênh đích.
- **Cập nhật thay vì gửi mới**: `messageId = 0` sẽ gửi tin nhắn đầu tiên rồi tự lưu ID để các lần sau *edit* vào đúng chỗ.
- **Presence Discord**: đặt trạng thái (`ONLINE`/`IDLE`/`DND`/`INVISIBLE`) và hoạt động (`PLAYING`/`WATCHING`/`LISTENING`...) kèm placeholder.
- **Retry tự động** khi Discord lỗi: số lần thử và thời gian chờ tăng dần (`retry.attempts`, `retry.backoffSeconds`).
- **Placeholder**: `{status}`, `{status_lower}`, `{server}`, `{time}`.
- Không có lệnh trong game — cấu hình hoàn toàn qua `config.yml`.

## Bảng lệnh

Plugin này **không có lệnh trong game** — toàn bộ cấu hình nằm trong `config.yml`.

| Lệnh | Quyền | Mô tả |
| --- | --- | --- |
| — | — | Không có lệnh; thay đổi `config.yml` rồi restart hoặc `/reload` |

## Cấu hình

```yaml
embed:
  title: "Trang thái máy chu"
  footer: "Cap nhat luc {time}"
  online:  { description: "Trang thai: ONLINE",  color: "#00ff75" }
  offline: { description: "Trang thai: OFFLINE", color: "#ff5555" }
  fields:
    - name: "Dia chi"
      value: "Java: Aefamily.fun  •  Bedrock: 19132"
      inline: false

retry:
  attempts: 3            # số lần thử lại khi gửi lỗi
  backoffSeconds: 5      # chờ tăng dần: 5s, 10s, 15s...

bots:
  - token: "BOT_TOKEN_1"        # ⚠️ thay bằng token thật, KHÔNG commit token thật
    presence:
      status: "ONLINE"
      activityType: "PLAYING"
      activityText: "May chu {status_lower}"
    channels:
      - channelId: "1383431339369435136"
        messageId: 0            # 0 = gửi mới rồi tự lưu ID
```

### Placeholder

| Placeholder | Giá trị |
| --- | --- |
| `{status}` | `ONLINE` / `OFFLINE` |
| `{status_lower}` | `online` / `offline` |
| `{server}` | Tên server Minecraft |
| `{time}` | Thời điểm cập nhật (`yyyy-MM-dd HH:mm:ss`) |

### Khi nào cập nhật

- `onEnable()` → gửi embed **ONLINE**.
- `onDisable()` → gửi embed **OFFLINE** trước khi ngắt kết nối JDA.

## Cài đặt

```bash
mvn clean package
```

1. Copy `target/Kayji-DiscordStatus-1.0-SNAPSHOT.jar` vào thư mục `plugins/`.
2. Chạy server một lần để tạo `plugins/Kayji-DiscordStatus/config.yml`.
3. Điền token bot Discord và `channelId` (bật intent **GUILD_MESSAGES** cho bot trong Discord Developer Portal).
4. Restart server.

> Maven Shade Plugin đóng gói JDA vào jar (loại trừ `spigot-api`).

## Cấu trúc dự án

```
├── pom.xml                                        Maven + Shade (Java 17)
└── src/main
    ├── java/com/aefamily/discordstatus
    │   └── DiscordStatusPlugin.java               Kết nối JDA, build embed, gửi/cập nhật + retry
    └── resources
        ├── plugin.yml                             Metadata (v2.2.0, không có lệnh)
        └── config.yml                             Embed, retry, danh sách bot + kênh
```

## Giấy phép

[GNU General Public License v3.0](LICENSE)
