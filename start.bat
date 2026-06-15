@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  Server Diamond - Paper Minecraft
REM  Tu dong mo overlay (http://localhost:8080) khi server san sang
REM  Tu dong tai Java 21 portable neu chua co
REM ============================================================

title Server Diamond - Paper Minecraft

REM --- Kiem tra va tai Java 21 neu can ---
if not exist "jre\bin\java.exe" (
    echo [INFO] Khong tim thay Java 21 cuc bo. Dang tu dong tai xuong Java 21 portable...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $url='https://cdn.azul.com/zulu/bin/zulu21.34.19-ca-jre21.0.3-win_x64.zip'; $zip='jre.zip'; Write-Host 'Dang tai Java 21 (Zulu JRE)...' -ForegroundColor Cyan; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri $url -OutFile $zip; Write-Host 'Dang giai nen...' -ForegroundColor Cyan; Expand-Archive -Path $zip -DestinationPath 'temp_jre'; $extracted = Get-ChildItem -Directory -Path 'temp_jre' | Select-Object -First 1; Move-Item -Path $extracted.FullName -Destination 'jre'; Remove-Item -Path 'temp_jre' -Recurse -Force; Remove-Item -Path $zip -Force; Write-Host 'Tai va cai dat Java portable hoan tat!' -ForegroundColor Green"
    if errorlevel 1 (
        echo [ERROR] Tai Java that bai. Vui long kiem tra ket noi mang va thu lai.
        pause
        exit /b 1
    )
)

set JAVA_BIN=jre\bin\java.exe
if not exist !JAVA_BIN! (
    set JAVA_BIN=java
)

REM --- Cho web overlay len roi tu dong mo trinh duyet (chay nen) ---
start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 120;$i++){try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('localhost',8080);$c.Close();Start-Process 'http://localhost:8080';break}catch{Start-Sleep -Milliseconds 1000}}"

REM --- Khoi dong server ---
echo [INFO] Dang khoi dong Minecraft Server bang Java: %JAVA_BIN%
%JAVA_BIN% -Xms2G -Xmx4G -jar paper.jar nogui

echo.
echo Server da dung. Nhan phim bat ky de dong cua so.
pause >nul
