param(
    [switch]$NonInteractive,
    [switch]$RunTests
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try { $Host.UI.RawUI.WindowTitle = "DONG GOI UNG DUNG BO QUOC PHONG" } catch { }

function Stop-Packaging {
    param([string]$Message)
    throw $Message
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   QUY TRINH DONG GOI UNG DUNG THAM DINH EXCEL BQP" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = (Get-Location).Path }

$JavaProjectDir = Join-Path $ScriptDir "bqp-validate-data"
$SrcStatic = Join-Path $JavaProjectDir "src\main\resources\static"
$OutputDir = Join-Path $ScriptDir "BQP_Application_Release"
$JarFile = Join-Path $JavaProjectDir "target\app-validate-excel-0.0.1-SNAPSHOT.jar"
$VerifierTmp = Join-Path $ScriptDir "launcher\PasswordVerifier_build_tmp.cs"
$ExitCode = 0

try {
    # Mat khau mac dinh theo yeu cau. Co the ghi de bang bien moi truong.
    $LauncherPassword = [Environment]::GetEnvironmentVariable("BQP_LAUNCHER_PASSWORD", "Process")
    if ([string]::IsNullOrWhiteSpace($LauncherPassword)) {
        $LauncherPassword = "123456"
        Write-Host "[THONG TIN] Su dung mat khau launcher mac dinh: 123456" -ForegroundColor Cyan
    } else {
        Write-Host "[OK] Da nhan mat khau launcher tu bien BQP_LAUNCHER_PASSWORD." -ForegroundColor Green
    }

    if ($LauncherPassword.Length -lt 4) {
        Stop-Packaging "Mat khau launcher phai co it nhat 4 ky tu."
    }

    Write-Host "[*] Dang tao salt va hash PBKDF2-HMAC-SHA256..." -ForegroundColor Yellow
    $SaltBytes = New-Object byte[] 16
    $Rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $Rng.GetBytes($SaltBytes)
    } finally {
        $Rng.Dispose()
    }

    $Pbkdf2 = [System.Security.Cryptography.Rfc2898DeriveBytes]::new(
        $LauncherPassword,
        $SaltBytes,
        200000,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256
    )
    try {
        $HashBytes = $Pbkdf2.GetBytes(32)
    } finally {
        $Pbkdf2.Dispose()
    }
    $SaltBase64 = [Convert]::ToBase64String($SaltBytes)
    $HashBase64 = [Convert]::ToBase64String($HashBytes)
    $LauncherPassword = $null
    Write-Host "[OK] Da tao salt/hash launcher." -ForegroundColor Green
    Write-Host ""

    $CscExe = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    if (-not (Test-Path -LiteralPath $CscExe)) {
        $CscExe = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    }
    $MavenCmd = Join-Path $JavaProjectDir "mvnw.cmd"

    foreach ($RequiredPath in @(
        $JavaProjectDir,
        $SrcStatic,
        $MavenCmd,
        (Join-Path $ScriptDir "launcher\PasswordVerifier.cs"),
        (Join-Path $ScriptDir "launcher\PasswordDialog.cs"),
        (Join-Path $ScriptDir "launcher\CongCuThamDinhBQP.cs"),
        $CscExe
    )) {
        if (-not (Test-Path -LiteralPath $RequiredPath)) {
            Stop-Packaging "Khong tim thay thanh phan bat buoc: $RequiredPath"
        }
    }

    Write-Host "[*] Dang giai phong cac tien trinh cu..." -ForegroundColor Yellow
    Get-Process -Name "Chay_CongCu_BQP", "BqpValidateExcel", "CongCuThamDinhBQP" -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*app-validate-excel*" } | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 800

    Write-Host "[1/6] Dang kiem tra dong bo tai nguyen giao dien..." -ForegroundColor Yellow
    $FilesToVerify = @(
        @{ Src = Join-Path $SrcStatic "index.html"; Alt = Join-Path $ScriptDir "cong_cu_tinh_toan_app4.0.html" },
        @{ Src = Join-Path $SrcStatic "app_validation.js"; Alt = Join-Path $ScriptDir "app_validation.js" },
        @{ Src = Join-Path $SrcStatic "app_normalization.js"; Alt = Join-Path $ScriptDir "app_normalization.js" }
    )
    foreach ($Item in $FilesToVerify) {
        if (-not (Test-Path -LiteralPath $Item.Src)) {
            Stop-Packaging "Thieu tai nguyen nguon: $($Item.Src)"
        }
        if (Test-Path -LiteralPath $Item.Alt) {
            $SourceHash = (Get-FileHash -LiteralPath $Item.Src -Algorithm SHA256).Hash
            $AlternateHash = (Get-FileHash -LiteralPath $Item.Alt -Algorithm SHA256).Hash
            if ($SourceHash -ne $AlternateHash) {
                Write-Host "[CANH BAO] Dang dong bo lai: $($Item.Alt)" -ForegroundColor DarkYellow
                Copy-Item -LiteralPath $Item.Src -Destination $Item.Alt -Force
            }
        }
    }
    Write-Host "[OK] Tai nguyen nguon da san sang." -ForegroundColor Green
    Write-Host ""

    Write-Host "[2/6] Kiem thu tu dong..." -ForegroundColor Yellow
    if ($RunTests) {
        $PackageJson = Join-Path $ScriptDir "package.json"
        if (-not (Test-Path -LiteralPath $PackageJson)) {
            Stop-Packaging "Khong tim thay package.json de chay kiem thu: $PackageJson"
        }
        Push-Location $ScriptDir
        try {
            & npm.cmd test
            if ($LASTEXITCODE -ne 0) {
                Stop-Packaging "Bo kiem thu tu dong that bai. Ma loi: $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
        Write-Host "[OK] Toan bo kiem thu da dat." -ForegroundColor Green
    } else {
        Write-Host "[BO QUA] Dong goi nhanh theo yeu cau, khong chay test noi bo." -ForegroundColor DarkYellow
    }

    Write-Host "[3/6] Dang dong goi Java JAR bang Maven..." -ForegroundColor Yellow
    Push-Location $JavaProjectDir
    try {
        & $MavenCmd clean package "-Dmaven.test.skip=true"
        if ($LASTEXITCODE -ne 0) {
            Stop-Packaging "Maven build that bai. Ma loi: $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $JarFile)) {
        Stop-Packaging "Khong tim thay file JAR sau khi build: $JarFile"
    }

    $TargetStatic = Join-Path $JavaProjectDir "target\classes\static"
    foreach ($Name in @("index.html", "app_validation.js", "app_normalization.js")) {
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

    Write-Host "[4/6] Dang bien dich Chay_CongCu_BQP.exe..." -ForegroundColor Yellow
    $VerifierSrc = Join-Path $ScriptDir "launcher\PasswordVerifier.cs"
    $DialogSrc = Join-Path $ScriptDir "launcher\PasswordDialog.cs"
    $MainSrc = Join-Path $ScriptDir "launcher\CongCuThamDinhBQP.cs"
    $TargetExe = Join-Path $ScriptDir "Chay_CongCu_BQP.exe"

    $VerifierContent = [System.IO.File]::ReadAllText($VerifierSrc, [System.Text.Encoding]::UTF8)
    $VerifierContent = [System.Text.RegularExpressions.Regex]::Replace(
        $VerifierContent,
        'SALT_BASE64\s*=\s*"[^"]*"',
        "SALT_BASE64        = `"$SaltBase64`""
    )
    $VerifierContent = [System.Text.RegularExpressions.Regex]::Replace(
        $VerifierContent,
        'HASH_BASE64\s*=\s*"[^"]*"',
        "HASH_BASE64        = `"$HashBase64`""
    )
    if ($VerifierContent.Contains("UNCONFIGURED_") -or
        -not $VerifierContent.Contains($SaltBase64) -or
        -not $VerifierContent.Contains($HashBase64)) {
        Stop-Packaging "Khong inject duoc salt/hash vao PasswordVerifier.cs."
    }
    [System.IO.File]::WriteAllText($VerifierTmp, $VerifierContent, [System.Text.Encoding]::UTF8)

    $WinFormsDll = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\System.Windows.Forms.dll"
    $DrawingDll = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\System.Drawing.dll"
    if (-not (Test-Path -LiteralPath $WinFormsDll)) {
        $WinFormsDll = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\System.Windows.Forms.dll"
        $DrawingDll = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\System.Drawing.dll"
    }

    & $CscExe /nologo "/out:$TargetExe" /target:exe /platform:anycpu /optimize+ `
        "/reference:$WinFormsDll" "/reference:$DrawingDll" `
        $VerifierTmp $DialogSrc $MainSrc
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $TargetExe)) {
        Stop-Packaging "Bien dich Chay_CongCu_BQP.exe that bai. Ma loi: $LASTEXITCODE"
    }

    $ExeText = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($TargetExe))
    if ($ExeText.Contains("UNCONFIGURED_")) {
        Stop-Packaging "Launcher sau bien dich van con placeholder mat khau."
    }
    Write-Host "[OK] Da bien dich launcher thanh cong." -ForegroundColor Green
    Write-Host ""

    Write-Host "[5/6] Dang tao thu muc phat hanh: $OutputDir" -ForegroundColor Yellow
    if (-not (Test-Path -LiteralPath $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    } else {
        Get-ChildItem -LiteralPath $OutputDir -Force | Remove-Item -Recurse -Force
    }

    Copy-Item -LiteralPath $TargetExe -Destination (Join-Path $OutputDir "Chay_CongCu_BQP.exe") -Force
    Copy-Item -LiteralPath $JarFile -Destination (Join-Path $OutputDir "app-validate-excel-0.0.1-SNAPSHOT.jar") -Force

    $JdkInstaller = Join-Path $ScriptDir "jdk-21.0.12_windows-x64_bin.exe"
    if (Test-Path -LiteralPath $JdkInstaller) {
        Copy-Item -LiteralPath $JdkInstaller -Destination (Join-Path $OutputDir "jdk-21.0.12_windows-x64_bin.exe") -Force
    }

    $DocumentationDir = Join-Path $OutputDir "Tai_lieu"
    New-Item -ItemType Directory -Path $DocumentationDir -Force | Out-Null
    $TemplateSrc = Join-Path $SrcStatic "PHU LUC KEM THEO HUONG DAN CUA CUC TAI CHINH.xlsx"
    if (Test-Path -LiteralPath $TemplateSrc) {
        Copy-Item -LiteralPath $TemplateSrc -Destination (Join-Path $DocumentationDir "PHU LUC KEM THEO HUONG DAN CUA CUC TAI CHINH.xlsx") -Force
    }

    $ReadmeContent = @"
======================================================================
 BO QUOC PHONG - CONG CU THAM DINH DU LIEU CHE DO CHINH SACH
 HUONG DAN CAI DAT VA SU DUNG
======================================================================

1. Cai Java JDK 21 bang file jdk-21.0.12_windows-x64_bin.exe neu may chua co Java.
2. Chay Chay_CongCu_BQP.exe.
3. Nhap mat khau truy cap do don vi phat hanh cung cap.
4. Khi khong su dung, nhan Enter trong cua so chuong trinh de thoat an toan.

Mat khau mac dinh cua ban dong goi nay: 123456
Co the thay doi khi dong goi bang bien moi truong BQP_LAUNCHER_PASSWORD.

De doi mat khau sau khi da chay:
- Mo ung dung qua Chay_CongCu_BQP.exe
- Nhan nut "Doi mat khau" tren thanh tieu de cua phan mem
- Mat khau moi duoc luu vao file bqp_password.dat canh thu muc chuong trinh
- Co hieu luc tu lan mo ung dung tiep theo
======================================================================
"@
    [System.IO.File]::WriteAllText(
        (Join-Path $OutputDir "Huong_Dan_Su_Dung.txt"),
        $ReadmeContent,
        [System.Text.Encoding]::UTF8
    )

    Write-Host "[6/6] Dang hoan tat goi phat hanh gon cho nguoi dung..." -ForegroundColor Yellow
    foreach ($InternalFile in @("Kiem_Tra_Toan_Ven.ps1", "RELEASE_MANIFEST_SHA256.txt", "VERSION.txt")) {
        $InternalPath = Join-Path $OutputDir $InternalFile
        if (Test-Path -LiteralPath $InternalPath) {
            Remove-Item -LiteralPath $InternalPath -Force
        }
    }
    Write-Host "[OK] Goi phat hanh chi con cac file nguoi dung can chay." -ForegroundColor Green
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host "[HOAN TAT] DA DONG GOI UNG DUNG BQP THANH CONG!" -ForegroundColor Green
    Write-Host "Thu muc phat hanh: $OutputDir" -ForegroundColor White
    Write-Host "Mat khau mac dinh : 123456" -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Cyan
}
catch {
    $ExitCode = 1
    Write-Host ""
    Write-Host "[LOI] $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if (Test-Path -LiteralPath $VerifierTmp) {
        Remove-Item -LiteralPath $VerifierTmp -Force -ErrorAction SilentlyContinue
    }
    $LauncherPassword = $null
    $HashBytes = $null
    $SaltBytes = $null
}

if (-not $NonInteractive -and $Host.Name -notmatch "ServerRemoteHost" -and [Environment]::UserInteractive) {
    Read-Host "Nhan Enter de ket thuc..."
}

exit $ExitCode
