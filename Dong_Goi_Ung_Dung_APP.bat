@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Dong_Goi_Ung_Dung_APP.ps1"
if %errorlevel% neq 0 (
    echo.
    echo Co loi xay ra trong qua trinh dong goi!
    pause
)
