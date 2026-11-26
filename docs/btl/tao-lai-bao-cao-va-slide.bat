@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Dựng lại báo cáo và slide Dippie
cd /d "%~dp0"

REM Đọc thong-tin-nhom.txt rồi dựng lại Bao_cao_BTL_Dippie.docx và Slide_BTL_Dippie.pptx.
echo Nhớ đóng Word và PowerPoint đang mở hai file báo cáo, slide trước khi chạy nhé.
echo.

set "PY="
where py >nul 2>nul && set "PY=py -3"
if not defined PY where python >nul 2>nul && set "PY=python"
if not defined PY goto no_python

%PY% -X utf8 -c "import docx, pptx, PIL" >nul 2>nul
if errorlevel 1 (
    echo Máy chưa có python-docx, python-pptx hoặc Pillow, mình cài luôn...
    %PY% -m pip install --user python-docx python-pptx pillow
    if errorlevel 1 goto failed
)

echo Đang dựng báo cáo...
%PY% -X utf8 build_report.py
if errorlevel 1 goto failed
echo Đang dựng slide...
%PY% -X utf8 build_slides.py
if errorlevel 1 goto failed

echo.
echo Xong rồi. Mở Bao_cao_BTL_Dippie.docx trong Word, khi Word hỏi cập nhật mục lục thì chọn Yes.
pause
exit /b 0

:no_python
echo Máy chưa cài Python 3. Tải ở https://www.python.org/downloads/ (nhớ tick "Add python.exe to PATH") rồi chạy lại file này.
echo Không muốn cài Python thì cứ mở thẳng file .docx và .pptx, gõ tên vào các dòng chấm là được.
pause
exit /b 1

:failed
echo.
echo Dựng chưa được. Kiểm tra xem Word hoặc PowerPoint còn mở file không, đóng đi rồi chạy lại.
pause
exit /b 1
