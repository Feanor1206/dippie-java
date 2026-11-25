@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Khởi chạy Dippie

REM Tệp chạy khi nhấp đúp trên Windows, chuyển hết tham số sang scripts\run-all.ps1.
set "SCRIPT_PATH=%~dp0scripts\run-all.ps1"
if not exist "%SCRIPT_PATH%" goto missing_script

set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS_EXE%" set "PS_EXE=powershell.exe"

REM Mở từ PowerShell 7 thì PSModulePath bị lẫn module của bản 7, khiến PowerShell 5.1 không nạp được module chuẩn.
REM Trả PSModulePath về đường dẫn mặc định của Windows PowerShell 5.1 cho phiên này.
set "PSModulePath=%ProgramFiles%\WindowsPowerShell\Modules;%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules"

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_PATH%" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" goto launch_failed

REM Nếu nhấp đúp trực tiếp (không truyền tham số) thì giữ cửa sổ lại để người chạy kịp xem tài khoản mẫu.
if "%~1"=="" goto pause_success
exit /b 0

:pause_success
echo.
echo Nhấn phím bất kỳ để đóng cửa sổ hướng dẫn này (Dippie vẫn tiếp tục chạy)...
pause >nul
exit /b 0

:missing_script
echo.
echo [LỖI] Không tìm thấy tệp khởi chạy: "%SCRIPT_PATH%"
echo Hãy kiểm tra bạn đã giải nén đầy đủ toàn bộ tệp ZIP trước khi chạy.
echo.
echo Nhấn phím bất kỳ để đóng cửa sổ...
pause >nul
exit /b 1

:launch_failed
echo.
echo ================================================================
echo   KHỞI CHẠY DIPPIE THẤT BẠI (mã lỗi: %EXIT_CODE%)
echo   Xem hướng dẫn xử lý sự cố trong tệp HUONG_DAN_CHAY.md.
echo ================================================================
echo.
echo Nhấn phím bất kỳ để đóng cửa sổ...
pause >nul
exit /b %EXIT_CODE%
