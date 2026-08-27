@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "PRODUCT_NAME=AabInstalllHelp"
set "EXE_NAME=%PRODUCT_NAME%.exe"
set "INSTALL_DIR=%LOCALAPPDATA%\Programs\%PRODUCT_NAME%"
set "SYNC_ASSETS=1"
set "SYNC_JAVA=0"
set "RESTART=1"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="/norestart" (
  set "RESTART=0"
  shift
  goto parse_args
)
if /I "%~1"=="/noassets" (
  set "SYNC_ASSETS=0"
  shift
  goto parse_args
)
if /I "%~1"=="/assets" (
  set "SYNC_ASSETS=1"
  set "SYNC_JAVA=1"
  shift
  goto parse_args
)
if /I "%~1"=="/installdir" (
  if "%~2"=="" (
    echo ERROR: /installdir requires a path
    goto fail
  )
  set "INSTALL_DIR=%~2"
  shift
  shift
  goto parse_args
)
echo ERROR: unknown argument %~1
echo Usage: pack_and_update.bat [/norestart] [/noassets] [/assets] [/installdir dir]
goto fail

:args_done
echo ========================================
echo  %PRODUCT_NAME% local pack and update
echo ========================================
echo.

where node >nul 2>&1
if errorlevel 1 (
  echo ERROR: node is not in PATH
  goto fail
)
where npm >nul 2>&1
if errorlevel 1 (
  echo ERROR: npm is not in PATH
  goto fail
)

if not exist "%INSTALL_DIR%\%EXE_NAME%" (
  echo ERROR: installed app not found:
  echo   %INSTALL_DIR%\%EXE_NAME%
  echo Install it once, or pass /installdir "D:\path\to\%PRODUCT_NAME%"
  goto fail
)

echo Install dir: %INSTALL_DIR%
echo.

echo [1/4] npm install --omit=dev
call npm install --omit=dev
if errorlevel 1 (
  echo ERROR: npm install failed
  goto fail
)
echo.

echo [2/4] pack app.asar
call node "scripts\pack-asar.js"
if errorlevel 1 (
  echo ERROR: pack app.asar failed
  goto fail
)
if not exist "release\app.asar" (
  echo ERROR: release\app.asar was not created
  goto fail
)
echo.

echo [3/4] stop running %EXE_NAME%
taskkill /F /IM "%EXE_NAME%" >nul 2>&1
set "WAIT=0"
:wait_exit
%SystemRoot%\System32\tasklist.exe /FI "IMAGENAME eq %EXE_NAME%" /NH 2>nul | %SystemRoot%\System32\findstr.exe /I /C:"%EXE_NAME%" >nul
if not errorlevel 1 (
  set /a WAIT+=1
  if !WAIT! GEQ 15 (
    echo ERROR: %EXE_NAME% is still running, cannot replace app.asar
    goto fail
  )
  timeout /t 1 /nobreak >nul
  goto wait_exit
)
echo.

echo [4/4] update installed files
set "RETRY=0"
:copy_asar
copy /Y "release\app.asar" "%INSTALL_DIR%\resources\app.asar" >nul
if errorlevel 1 (
  set /a RETRY+=1
  if !RETRY! GEQ 10 (
    echo ERROR: cannot replace app.asar
    goto fail
  )
  timeout /t 1 /nobreak >nul
  goto copy_asar
)
echo updated resources\app.asar

if "%SYNC_ASSETS%"=="1" (
  if not exist "%INSTALL_DIR%\resources\assets" mkdir "%INSTALL_DIR%\resources\assets"
  if exist "assets_common\" (
    robocopy "assets_common" "%INSTALL_DIR%\resources\assets" /E /XO /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
    if errorlevel 8 (
      echo ERROR: failed to copy assets_common
      goto fail
    )
  )
  if exist "bin_win\" (
    if "%SYNC_JAVA%"=="0" if not exist "%INSTALL_DIR%\resources\assets\java\bin\java.exe" set "SYNC_JAVA=1"
    if "%SYNC_JAVA%"=="1" (
      robocopy "bin_win" "%INSTALL_DIR%\resources\assets" /E /XO /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
    ) else (
      robocopy "bin_win" "%INSTALL_DIR%\resources\assets" /E /XO /XD java /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
    )
    if errorlevel 8 (
      echo ERROR: failed to copy bin_win
      goto fail
    )
  )
  if exist "config\common.json" (
    for %%I in ("config\common.json") do if %%~zI GTR 0 (
      copy /Y "config\common.json" "%INSTALL_DIR%\resources\assets\common.json" >nul
    )
  )
  echo synced resources\assets
)

if "%RESTART%"=="1" (
  echo starting %EXE_NAME%
  start "" "%INSTALL_DIR%\%EXE_NAME%"
)

echo.
echo Done. Installed app updated.
goto end

:fail
echo.
echo Failed.
exit /b 1

:end
exit /b 0
