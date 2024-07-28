@echo off

%NAV_BINARY% %*

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
