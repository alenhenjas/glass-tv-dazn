@echo off
rem Glass TV - ripristino completo del dazn box (c) 2026 Alen Henjas
call "%~dp0_env.bat"
echo riattivo le app disattivate e ripristino le impostazioni originali.
pause
for /f "usebackq eol=# tokens=1" %%p in ("%~dp0debloat.txt") do %S% pm enable %%p
if exist "%~dp0backup_impostazioni.txt" (
  for /f "usebackq tokens=1-3" %%a in ("%~dp0backup_impostazioni.txt") do (
    if "%%c"=="null" (%S% settings delete %%a %%b) else (%S% settings put %%a %%b %%c)
  )
)
%S% cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
"%ADB%" -s %BOX%:5555 reboot
echo fatto: il box si riavvia con la home originale.
pause
