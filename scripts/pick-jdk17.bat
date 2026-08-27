@echo off
rem Sets JAVA_HOME to a JDK 17+ directory. No setlocal so the caller inherits it.
set "PICK_JDK="
for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pick-jdk17.ps1"`) do set "PICK_JDK=%%I"
if not defined PICK_JDK exit /b 1
if not exist "%PICK_JDK%\bin\java.exe" exit /b 1
set "JAVA_HOME=%PICK_JDK%"
exit /b 0
