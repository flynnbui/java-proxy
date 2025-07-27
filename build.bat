@echo off
REM Build script for Java HTTP Proxy

set SRC_DIR=src\main\java
set BUILD_DIR=build
set OUT_DIR=out
set MAIN_CLASS=proxy.HttpProxy

if "%1"=="" goto build
if "%1"=="build" goto build
if "%1"=="build-out" goto build-out
if "%1"=="clean" goto clean
if "%1"=="run" goto run
if "%1"=="test" goto test
if "%1"=="help" goto help
goto help

:build
echo Creating build directory...
if not exist %BUILD_DIR% mkdir %BUILD_DIR%
echo Compiling Java proxy...
javac -d %BUILD_DIR% -cp %SRC_DIR% %SRC_DIR%\proxy\*.java %SRC_DIR%\proxy\cache\*.java %SRC_DIR%\proxy\config\*.java %SRC_DIR%\proxy\http\*.java %SRC_DIR%\proxy\logging\*.java %SRC_DIR%\proxy\middleware\*.java %SRC_DIR%\proxy\server\*.java %SRC_DIR%\proxy\utils\*.java
echo Build complete!
goto end

:build-out
echo Creating out directory...
if not exist %OUT_DIR% mkdir %OUT_DIR%
echo Compiling to out directory...
javac -d %OUT_DIR% -cp %SRC_DIR% %SRC_DIR%\proxy\*.java %SRC_DIR%\proxy\cache\*.java %SRC_DIR%\proxy\config\*.java %SRC_DIR%\proxy\http\*.java %SRC_DIR%\proxy\logging\*.java %SRC_DIR%\proxy\middleware\*.java %SRC_DIR%\proxy\server\*.java %SRC_DIR%\proxy\utils\*.java
echo Build complete!
goto end

:clean
echo Cleaning build artifacts...
if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
if exist %OUT_DIR% rmdir /s /q %OUT_DIR%
echo Clean complete!
goto end

:run
call :build
echo Starting HTTP proxy on port 8080...
java -cp %BUILD_DIR% %MAIN_CLASS% 8080 30 102400 1048576
goto end

:test
call :build
echo Running comprehensive tests...
python testScript\test_comprehensive.py
goto end

:help
echo Usage: build.bat [command]
echo.
echo Commands:
echo   build       - Build the project (default)
echo   build-out   - Build to 'out' directory
echo   clean       - Remove build artifacts
echo   run         - Build and run with default settings
echo   test        - Build and run tests
echo   help        - Show this help message
goto end

:end
