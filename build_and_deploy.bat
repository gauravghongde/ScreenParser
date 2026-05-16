@echo off
:: ============================================================
::  ScrollCapture — Deploy with android-deploy-kit
::  Usage: build_and_deploy.bat [dev|apk|bundle]
::  Defaults: dev
:: ============================================================
setlocal

set MODE=%~1

if "%MODE%"=="" set MODE=dev

:: Add python to PATH or use existing python
python -m android_deploy_kit --help >nul 2>&1
if errorlevel 1 (
    echo [deploy] Installing android-deploy-kit...
    pip install -e C:\Users\26san\StudioProjects\android-deploy-kit
)

echo.
echo ======================================================
echo  Deploying ScrollCapture [%MODE%]
echo ======================================================
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PYTHONPATH=C:\Users\26san\StudioProjects\android-deploy-kit\src
python -m android_deploy_kit --config deploy\scrollcapture.deploy.json %MODE%

if errorlevel 1 (
    echo [ERROR] Deploy failed.
    exit /b 1
)
