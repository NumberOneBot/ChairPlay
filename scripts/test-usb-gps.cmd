@echo off
rem Runs the USB GPS helper's Python tests (tools\usb-gps) with a time limit. The Python is the one in
rem tools\usb-gps\.venv, or the one CHAIRPLAY_USB_GPS_PYTHON names. Arguments go to unittest, for example
rem   scripts\test-usb-gps.cmd tests.test_protocol
rem The tests use fake phones only: none of them opens a USB device.
setlocal EnableExtensions
set "TOOLS=%~dp0..\tools\usb-gps"
set "PY=%CHAIRPLAY_USB_GPS_PYTHON%"
if not defined PY set "PY=%TOOLS%\.venv\Scripts\python.exe"
if not exist "%PY%" (
    echo test-usb-gps: no Python at "%PY%". Create tools\usb-gps\.venv with requirements.txt, or set CHAIRPLAY_USB_GPS_PYTHON. 1>&2
    exit /b 1
)
"%PY%" "%TOOLS%\run_tests.py" --timeout 300 %*
