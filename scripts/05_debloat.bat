@echo off
rem Glass TV - disattiva (in modo reversibile) le app elencate in debloat.txt (c) 2026 Alen Henjas
call "%~dp0_env.bat"
set "L=%~dp0disattivati_log.txt"
for /f "usebackq eol=# tokens=1" %%p in ("%~dp0debloat.txt") do (
  echo - %%p
  %S% pm disable-user --user 0 %%p
  >> "%L%" echo %%p
)
echo fatto: elenco in disattivati_log.txt (serve a 99_ripristino.bat)
pause
