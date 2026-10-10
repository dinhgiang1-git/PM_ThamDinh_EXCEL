param(
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try { $Host.UI.RawUI.WindowTitle = "DONG GOI PHAN MEM RA SOAT, THAM DINH CHE DO NGHI HUU, PHUC VIEN" } catch { }

function Stop-Packaging {
    param([string]$Message)
    throw $Message
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   QUY TRINH DONG GOI UNG DUNG THAM DINH EXCEL BQP" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

# Script nam o thu muc goc cua repo (cung cap voi pom.xml)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = (Get-Location).Path }

$JavaProjectDir = $ScriptDir
$SrcStatic = Join-Path $JavaProjectDir "src\main\resources\static"
$OutputDir = Join-Path $ScriptDir "PhanMemThamDinh_Application_Release"
$TemplateName = "PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx"
$ExitCode = 0

try {
    $MavenCmd = Join-Path $JavaProjectDir "mvnw.cmd"

    foreach ($RequiredPath in @(
        (Join-Path $JavaProjectDir "pom.xml"),
        $SrcStatic,
        $MavenCmd,
        (Join-Path $SrcStatic $TemplateName)
    )) {
        if (-not (Test-Path -LiteralPath $RequiredPath)) {
            Stop-Packaging "Khong tim thay thanh phan bat buoc: $RequiredPath"
        }
    }

    Write-Host "[1/4] Dang tat ung dung dang chay (neu co)..." -ForegroundColor Yellow
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*app-validate-excel*" } | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 800
    Write-Host ""

    Write-Host "[2/4] Dang dong goi Java JAR bang Maven..." -ForegroundColor Yellow
    Push-Location $JavaProjectDir
    try {
        & $MavenCmd clean package "-Dmaven.test.skip=true"
        if ($LASTEXITCODE -ne 0) {
            Stop-Packaging "Maven build that bai. Ma loi: $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    $BuiltJar = Get-ChildItem -LiteralPath (Join-Path $JavaProjectDir "target") -Filter "app-validate-excel-*.jar" |
        Where-Object { $_.Name -notlike "*.original" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $BuiltJar) {
        Stop-Packaging "Khong tim thay file JAR sau khi build trong thu muc target."
    }
    Write-Host "[OK] Su dung JAR: $($BuiltJar.Name)" -ForegroundColor Green

    $TargetStatic = Join-Path $JavaProjectDir "target\classes\static"
    foreach ($Name in @("index.html", "app_validation.js", "app_normalization.js", "app_compare.js")) {
        $SourceFile = Join-Path $SrcStatic $Name
        $TargetFile = Join-Path $TargetStatic $Name
        if (-not (Test-Path -LiteralPath $TargetFile)) {
            Stop-Packaging "Thieu tai nguyen sau Maven build: $TargetFile"
        }
        if ((Get-FileHash -LiteralPath $SourceFile -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $TargetFile -Algorithm SHA256).Hash) {
            Stop-Packaging "Tai nguyen trong target khong khop ma nguon: $Name"
        }
    }
    Write-Host "[OK] JAR va tai nguyen da duoc build dong bo." -ForegroundColor Green
    Write-Host ""

    Write-Host "[3/4] Dang tao thu muc phat hanh: $OutputDir" -ForegroundColor Yellow
    if (-not (Test-Path -LiteralPath $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    } else {
        Get-ChildItem -LiteralPath $OutputDir -Force | Remove-Item -Recurse -Force
    }

    Copy-Item -LiteralPath $BuiltJar.FullName -Destination (Join-Path $OutputDir "app-validate-excel-0.0.1-SNAPSHOT.jar") -Force

    $StartBat = @'
@echo off
cd /d "%~dp0"
rem Giai phong cong 8080: tat cac tien trinh dang chiem cong truoc khi khoi dong
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Write-Host ('Tat tien trinh chiem cong 8080: PID ' + $_.OwningProcess); Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
rem Chay ngam: doi cong 8080 san sang roi tu mo trinh duyet
start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for ($i=0; $i -lt 120; $i++) { try { (New-Object Net.Sockets.TcpClient('127.0.0.1',8080)).Close(); Start-Process 'http://localhost:8080/'; break } catch { Start-Sleep -Milliseconds 500 } }"
java -jar app-validate-excel-0.0.1-SNAPSHOT.jar
echo ===== DONE =====
pause
'@
    $StopBat = @'
@echo off
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*app-validate-excel*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
echo ALL SERVICES STOPPED
pause
'@
    [System.IO.File]::WriteAllText((Join-Path $OutputDir "start.bat"), $StartBat, [System.Text.Encoding]::ASCII)
    [System.IO.File]::WriteAllText((Join-Path $OutputDir "stop.bat"), $StopBat, [System.Text.Encoding]::ASCII)

    # Bo cai JDK (khong nam trong repo): neu dat file nay canh script thi se duoc chep kem
    $JdkInstaller = Join-Path $ScriptDir "jdk-21.0.12_windows-x64_bin.exe"
    if (Test-Path -LiteralPath $JdkInstaller) {
        Copy-Item -LiteralPath $JdkInstaller -Destination (Join-Path $OutputDir "jdk-21.0.12_windows-x64_bin.exe") -Force
    }

    $DocumentationDir = Join-Path $OutputDir "Tai_lieu"
    New-Item -ItemType Directory -Path $DocumentationDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $SrcStatic $TemplateName) -Destination (Join-Path $DocumentationDir $TemplateName) -Force

    $ReadmeContent = @"
======================================================================
 PHAN MEM PHUC VU RA SOAT, THAM DINH CHE DO NGHI HUU, PHUC VIEN
 (THEO NGHI DINH SO 177/2024/ND-CP, NGHI DINH SO 178/2024/ND-CP VA NGHI DINH SO 67/2025/ND-CP)
 HUONG DAN CAI DAT VA SU DUNG
======================================================================

1. Cai Java JDK 21 neu may chua co Java.
2. Chay start.bat de khoi dong ung dung; trinh duyet tu mo tai http://localhost:8080.
3. Chay stop.bat de tat ung dung.
4. File co so du lieu (app-data.db) nam ngay canh file jar.
======================================================================
"@
    [System.IO.File]::WriteAllText(
        (Join-Path $OutputDir "Huong_Dan_Su_Dung.txt"),
        $ReadmeContent,
        [System.Text.Encoding]::UTF8
    )

    Write-Host "[4/4] Hoan tat." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host "[HOAN TAT] DA DONG GOI UNG DUNG BQP THANH CONG!" -ForegroundColor Green
    Write-Host "Thu muc phat hanh: $OutputDir" -ForegroundColor White
    Write-Host "======================================================================" -ForegroundColor Cyan
}
catch {
    $ExitCode = 1
    Write-Host ""
    Write-Host "[LOI] $($_.Exception.Message)" -ForegroundColor Red
}

if (-not $NonInteractive -and $Host.Name -notmatch "ServerRemoteHost" -and [Environment]::UserInteractive) {
    Read-Host "Nhan Enter de ket thuc..."
}

exit $ExitCode
