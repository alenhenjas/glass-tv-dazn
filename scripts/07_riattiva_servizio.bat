@echo off
rem Glass TV - riattiva il servizio dopo un aggiornamento di glass (c) 2026 Alen Henjas
call "%~dp0_env.bat"
%S% settings put secure enabled_accessibility_services null
timeout /t 1 >nul
%S% settings put secure enabled_accessibility_services com.alen.glass/com.alen.glass.GlassService
%S% settings put secure accessibility_enabled 1
echo servizio glass riattivato
pause
