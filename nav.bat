@echo off

%~dp0app\build\bin\mingwX64\debugExecutable\app.exe %*

set navFile=%USERPROFILE%\.nav-cd
if exist "%navFile%" call :handleNavFile
goto :eof

:handleNavFile
set /p newDir=<"%navFile%"
if not "!newDir!"=="" (
    cd /d "%newDir%"
    del "%navFile%"
)
goto :eof