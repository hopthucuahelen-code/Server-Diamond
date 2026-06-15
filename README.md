# Server Diamond - Paper Minecraft

Minecraft Server 1.21.1 phục vụ livestream tương tác.

## Hướng dẫn cho người nhận (Bạn của bạn)

Để tải và chạy server này (bao gồm cả việc tự động tải Java 21 portable nếu máy chưa có), hãy làm theo các bước sau:

1. Mở **Command Prompt (cmd)** trên máy tính Windows.
2. Sao chép câu lệnh dưới đây, thay thế `TÊN_TÀI_KHOẢN` và `TÊN_KHO_LƯU_TRỮ` bằng thông tin Repo GitHub của bạn, rồi dán vào CMD và nhấn Enter:

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://github.com/TÊN_TÀI_KHOẢN/TÊN_KHO_LƯU_TRỮ/archive/refs/heads/main.zip' -OutFile 'server.zip'; Expand-Archive -Path 'server.zip' -DestinationPath '.'; Remove-Item 'server.zip'; cd 'TÊN_KHO_LƯU_TRỮ-main'; .\start.bat"
```

*Lưu ý: Bạn cũng có thể tải thủ công file ZIP của repository này từ GitHub, giải nén ra và chạy trực tiếp file `start.bat`.*

## Cơ chế hoạt động của start.bat
File `start.bat` đã được cấu hình để tự động kiểm tra xem thư mục `jre/` (chứa Java portable) có tồn tại hay không. Nếu chưa có, nó sẽ tự động tải Java 21 JRE từ Eclipse Temurin (Adoptium), giải nén vào thư mục `jre/` và dùng Java này để chạy server. Máy của bạn của bạn sẽ không cần phải cài đặt Java thủ công từ trước.
