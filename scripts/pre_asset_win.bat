@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if exist "assets\" (
  rmdir /s /q "assets"
  if exist "assets\" (
    echo ERROR: cannot remove assets\
    exit /b 1
  )
)

mkdir "assets"
if errorlevel 1 (
  echo ERROR: cannot create assets\
  exit /b 1
)

if not exist "assets_common\" (
  echo ERROR: assets_common\ is missing
  exit /b 1
)
if not exist "bin_win\" (
  echo ERROR: bin_win\ is missing
  exit /b 1
)

xcopy "assets_common\*" "assets\" /E /I /Y >nul
if errorlevel 1 (
  echo ERROR: failed to copy assets_common
  exit /b 1
)

xcopy "bin_win\*" "assets\" /E /I /Y >nul
if errorlevel 1 (
  echo ERROR: failed to copy bin_win
  exit /b 1
)

echo pre_asset_win done
exit /b 0
