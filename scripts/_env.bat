@echo off
rem Glass TV - configurazione comune degli script (c) 2026 Alen Henjas
rem adb: prima quello dell'sdk android, poi la cartella platform-tools accanto agli script
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%~dp0platform-tools\adb.exe"
set "BOX="
if exist "%~dp0ip.txt" set /p BOX=<"%~dp0ip.txt"
set "S="%ADB%" -s %BOX%:5555 shell"
