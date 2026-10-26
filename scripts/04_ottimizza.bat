@echo off
rem Glass TV - ottimizzazioni dazn tv box (sagemcom dtiw3930) (c) 2026 Alen Henjas
call "%~dp0_env.bat"
set "B=%~dp0backup_impostazioni.txt"
if not exist "%B%" (
  %S% "for k in window_animation_scale transition_animation_scale animator_duration_scale wifi_scan_always_enabled ble_scan_always_enabled hdmi_control_enabled; do echo global $k $(settings get global $k); done; echo secure screensaver_enabled $(settings get secure screensaver_enabled)" > "%B%"
  echo backup salvato in backup_impostazioni.txt
)
echo [1/4] animazioni rapide (0.35x finestre, 0.5x app)
%S% settings put global window_animation_scale 0.35
%S% settings put global transition_animation_scale 0.35
%S% settings put global animator_duration_scale 0.5
echo [2/4] niente scansioni wi-fi e bluetooth in background
%S% settings put global wifi_scan_always_enabled 0
%S% settings put global ble_scan_always_enabled 0
echo [3/4] hdmi-cec attivo (glass spegne anche la tv)
%S% settings put global hdmi_control_enabled 1
%S% settings put global hdmi_control_auto_device_off_enabled 1
echo [4/4] pulizia cache e compilazione aot delle app (qualche minuto)
%S% pm trim-caches 64G
%S% cmd package bg-dexopt-job
echo fatto.
pause
