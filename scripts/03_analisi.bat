@echo off
rem Glass TV - analisi completa del box in analisi.txt (c) 2026 Alen Henjas
call "%~dp0_env.bat"
set "T=%~dp0analisi.txt"
echo raccolgo informazioni...
> "%T%" echo ===== SISTEMA =====
%S% "for p in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.board.platform ro.hardware ro.product.cpu.abilist; do echo $p = $(getprop $p); done" >> "%T%"
>> "%T%" echo ===== CPU / RAM =====
%S% "cat /proc/cpuinfo | grep -iE 'Hardware|CPU part' | sort | uniq -c; head -3 /proc/meminfo" >> "%T%"
>> "%T%" echo ===== DISPLAY / HDR =====
%S% "dumpsys display | grep -m3 -iE 'supportedModes|HdrCapabilities'; dumpsys SurfaceFlinger | grep -iE 'FixedSizeModeMgr|Max supported DV'" >> "%T%"
>> "%T%" echo ===== RETE =====
%S% cmd wifi status >> "%T%" 2>&1
>> "%T%" echo ===== LAUNCHER =====
%S% cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME >> "%T%"
>> "%T%" echo ===== IMPOSTAZIONI =====
%S% settings list global >> "%T%"
%S% settings list secure >> "%T%"
>> "%T%" echo ===== PACCHETTI =====
%S% pm list packages -s >> "%T%"
%S% pm list packages -3 >> "%T%"
echo fatto: analisi.txt
pause
