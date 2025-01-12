@echo off

@rem This is an example file for a possible batch based implementation

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
