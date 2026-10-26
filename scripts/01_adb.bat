@echo off
rem Glass TV - verifica adb o scarica i platform-tools ufficiali di google (c) 2026 Alen Henjas
call "%~dp0_env.bat"
if exist "%ADB%" ( echo adb trovato: %ADB% & "%ADB%" version & pause & exit /b 0 )
echo adb non trovato: scarico platform-tools da dl.google.com...
powershell -NoProfile -Command "Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile '%~dp0pt.zip'; Expand-Archive -Force '%~dp0pt.zip' '%~dp0'; Remove-Item '%~dp0pt.zip'"
call "%~dp0_env.bat"
if exist "%ADB%" (echo ok: %ADB%) else (echo ERRORE: download non riuscito)
pause
