# ============================================================
#  Script tai cac plugin co ban cho Server Diamond (Paper)
#  Chay: chuot phai -> Run with PowerShell
#  Hoac: powershell -ExecutionPolicy Bypass -File install-plugins.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$pluginDir = Join-Path $PSScriptRoot "plugins"

if (-not (Test-Path $pluginDir)) {
    New-Item -ItemType Directory -Path $pluginDir | Out-Null
}

# Danh sach plugin co ban: ten file = duong dan tai
$plugins = [ordered]@{
    "EssentialsX.jar"      = "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsX-2.22.0.jar"
    "EssentialsXChat.jar"  = "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsXChat-2.22.0.jar"
    "EssentialsXSpawn.jar" = "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsXSpawn-2.22.0.jar"
    "Vault.jar"            = "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar"
}

Write-Host "Dang tai plugin vao: $pluginDir" -ForegroundColor Cyan
Write-Host ""

foreach ($name in $plugins.Keys) {
    $dest = Join-Path $pluginDir $name
    try {
        Write-Host ("  -> {0,-24}" -f $name) -NoNewline
        Invoke-WebRequest -Uri $plugins[$name] -OutFile $dest -UseBasicParsing
        $kb = [math]::Round((Get-Item $dest).Length / 1KB, 0)
        Write-Host "OK ($kb KB)" -ForegroundColor Green
    } catch {
        Write-Host "LOI: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Hoan tat! Khoi dong lai server (start.bat) de nap plugin." -ForegroundColor Cyan
if ($Host.Name -eq "ConsoleHost") {
    Write-Host "Nhan phim bat ky de dong..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
