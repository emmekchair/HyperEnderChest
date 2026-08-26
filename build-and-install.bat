@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=%~dp0.tools\jdk\jdk-25.0.4.1+1"
set "SOURCE=%~dp0build\libs\HyperEnderChest-1.0.0.jar"
set "TARGET=C:\Users\Administrator\Desktop\Paper26.2\plugins\HyperEnderChest-1.0.0.jar"

call gradlew.bat clean test build
if errorlevel 1 exit /b %errorlevel%

copy /y "%SOURCE%" "%TARGET%" >nul
if errorlevel 1 exit /b %errorlevel%

echo Built and installed: %TARGET%
