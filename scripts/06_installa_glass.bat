@echo off
rem Glass TV - installazione completa: apk, permessi, servizio, home predefinita (c) 2026 Alen Henjas
call "%~dp0_env.bat"
set "APK=%~dp0..\release\GlassTV.apk"
"%ADB%" connect %BOX%:5555
echo [1/5] installo Glass TV
"%ADB%" -s %BOX%:5555 install -r "%APK%"
echo [2/5] permessi speciali
%S% pm grant com.alen.glass android.permission.WRITE_SECURE_SETTINGS
%S% pm grant com.alen.glass android.permission.DUMP
%S% pm grant com.alen.glass android.permission.ACCESS_FINE_LOCATION
%S% pm grant com.alen.glass android.permission.ACCESS_COARSE_LOCATION
%S% appops set com.alen.glass WRITE_SETTINGS allow
%S% appops set com.alen.glass PROJECT_MEDIA allow
%S% appops set com.alen.glass SYSTEM_ALERT_WINDOW allow
echo [3/5] compilazione ottimizzata
%S% cmd package compile -m speed -f com.alen.glass
echo [4/5] servizio glass (volume, impostazioni, multitasking)
%S% settings put secure enabled_accessibility_services com.alen.glass/com.alen.glass.GlassService
%S% settings put secure accessibility_enabled 1
echo [5/5] glass come schermata home
%S% pm disable-user --user 0 com.google.android.tvlauncher
%S% cmd package set-home-activity com.alen.glass/.MainActivity
%S% input keyevent 3
echo fatto: premi HOME sul telecomando.
pause
