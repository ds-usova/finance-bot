@echo off
setlocal

set "REPO=%~dp0..\.."
for %%I in ("%REPO%") do set "REPO=%%~fI"
set "PLUGIN=%REPO%\..\tdd-sdlc"
for %%I in ("%PLUGIN%") do set "PLUGIN=%%~fI"

if not exist "%PLUGIN%\" (
    echo tdd-sdlc checkout not found at %PLUGIN%
    echo Clone it beside this repository first.
    exit /b 1
)

cd /d "%REPO%"
claude --plugin-dir "%PLUGIN%" --add-dir "%PLUGIN%" %*
