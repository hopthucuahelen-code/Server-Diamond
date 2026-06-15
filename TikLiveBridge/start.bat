@echo off
REM ============================================================
REM  TikLive Bridge - cau noi TikTok Live -> Minecraft
REM  Mo dashboard tu dong sau khi server len.
REM ============================================================
title TikLive Bridge - Server Diamond
cd /d "%~dp0"

if not exist node_modules (
  echo Cai dat thu vien lan dau...
  call npm install
)

start "" /b powershell -NoProfile -WindowStyle Hidden -Command ^
  "for($i=0;$i -lt 60;$i++){try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('localhost',8088);$c.Close();Start-Process 'http://localhost:8088';break}catch{Start-Sleep -Milliseconds 500}}"

node engine/server.js
echo.
echo TikLive Bridge da dung. Nhan phim bat ky de dong.
pause >nul
