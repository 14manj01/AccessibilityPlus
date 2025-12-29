\
@echo off
setlocal enabledelayedexpansion

REM Accessibility Plus speech bridge starter (Windows)
REM 1) Copy config.example.env -> config.env
REM 2) Edit config.env and set PIPER_PATH, MODEL_PATH, CONFIG_PATH

set SCRIPT_DIR=%~dp0
set CFG=%SCRIPT_DIR%config.env

if not exist "%CFG%" (
  echo [ERROR] config.env not found.
  echo Copy speech-bridge\config.example.env to speech-bridge\config.env and edit paths.
  pause
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%CFG%") do (
  set K=%%A
  set V=%%B
  if "!K!"=="PIPER_PATH" set PIPER_PATH=!V!
  if "!K!"=="MODEL_PATH" set MODEL_PATH=!V!
  if "!K!"=="CONFIG_PATH" set CONFIG_PATH=!V!
  if "!K!"=="PORT" set PORT=!V!
)

if "%PORT%"=="" set PORT="59125"

echo Starting Accessibility Plus Piper bridge on port %PORT%
python "%SCRIPT_DIR%piper_bridge_server.py" --port %PORT% --piper %PIPER_PATH% --model %MODEL_PATH% --config %CONFIG_PATH%
pause
