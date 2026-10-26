@echo off
rem Glass TV - collegamento adb via rete (c) 2026 Alen Henjas
call "%~dp0_env.bat"
if not "%BOX%"=="" echo ip salvato: %BOX% (invio per tenerlo)
set /p NEWIP=IP del box (es. 192.168.1.50): 
if not "%NEWIP%"=="" set "BOX=%NEWIP%"
> "%~dp0ip.txt" echo %BOX%
"%ADB%" connect %BOX%:5555
echo.
echo sul televisore conferma "Consentire il debug USB?" con "Consenti sempre", poi rilancia:
echo lo stato deve essere "device" (non "unauthorized").
"%ADB%" devices
pause
