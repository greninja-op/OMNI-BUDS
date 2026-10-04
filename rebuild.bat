@echo off
setlocal enabledelayedexpansion

echo ======================================================================
echo                     OmniBuds Environment Rebuild
echo ======================================================================

:: 1. Locate Java 17 / JDK
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        goto java_found
    )
)

:: Search common JDK 17 paths
if exist "%LOCALAPPDATA%\jdk-17\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\jdk-17"
    goto java_found
)

for /d %%D in ("%ProgramFiles%\Java\jdk-17*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        goto java_found
    )
)

for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        goto java_found
    )
)

if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    goto java_found
)

where java >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] Using Java from PATH.
    goto java_check_done
)

echo [ERROR] JAVA_HOME is not set and Java 17 was not found.
echo Please install JDK 17 or set JAVA_HOME to your JDK 17 directory.
exit /b 1

:java_found
echo [INFO] Using JAVA_HOME: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

:java_check_done

:: 2. Check Android SDK and local.properties
if not exist "local.properties" (
    echo [WARN] local.properties not found. Attempting auto-detection...
    if defined ANDROID_HOME (
        echo sdk.dir=%ANDROID_HOME:\=/%> local.properties
        echo [INFO] Created local.properties from ANDROID_HOME.
    ) else if defined ANDROID_SDK_ROOT (
        echo sdk.dir=%ANDROID_SDK_ROOT:\=/%> local.properties
        echo [INFO] Created local.properties from ANDROID_SDK_ROOT.
    ) else if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "SDK_PATH=%LOCALAPPDATA%\Android\Sdk"
        echo sdk.dir=!SDK_PATH:\=/!> local.properties
        echo [INFO] Created local.properties pointing to %LOCALAPPDATA%\Android\Sdk.
    ) else (
        echo [ERROR] Could not find Android SDK. Please copy local.properties.example to local.properties and configure sdk.dir.
        exit /b 1
    )
) else (
    echo [INFO] local.properties detected.
)

:: 3. Clean and build Gradle modules
echo.
echo [1/3] Building all Gradle modules (:core, :platform:android, :tools:companion-shell)...
call gradlew.bat build
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Gradle build failed.
    exit /b %ERRORLEVEL%
)

:: 4. Run tests
echo.
echo [2/3] Running module test suites...
call gradlew.bat test
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Gradle tests failed.
    exit /b %ERRORLEVEL%
)

:: 5. Run Python device-bridge test suite
echo.
echo [3/3] Running Python device-bridge verification suite...
where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    python tools\device-bridge\verify_suite.py
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Python verification suite failed.
        exit /b !ERRORLEVEL!
    )
) else (
    echo [WARN] Python not found on PATH. Skipping device-bridge self-check.
)

echo.
echo ======================================================================
echo              OmniBuds Rebuild Completed Successfully!
echo ======================================================================
exit /b 0
