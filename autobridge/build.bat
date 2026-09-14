@echo off
REM AutoBridge build script
REM Requires Java 21+ on PATH or set JAVA_HOME

set JAVA_EXE=java
set JAVAC_EXE=javac
set JAR_EXE=jar

REM Try to find Java from PrismLauncher if not on PATH
where java >nul 2>nul
if errorlevel 1 (
    if exist "%APPDATA%\PrismLauncher\java\java-runtime-epsilon\bin\java.exe" (
        set "JAVA_EXE=%APPDATA%\PrismLauncher\java\java-runtime-epsilon\bin\java.exe"
        set "JAVAC_EXE=%APPDATA%\PrismLauncher\java\java-runtime-epsilon\bin\javac.exe"
        set "JAR_EXE=%APPDATA%\PrismLauncher\java\java-runtime-epsilon\bin\jar.exe"
    ) else (
        echo ERROR: Java not found. Install Java 21+ or set JAVA_HOME.
        exit /b 1
    )
)

echo === AutoBridge Build ===
echo.

REM Create output dirs
mkdir build\classes 2>nul
mkdir build\jar-staging\autobridge 2>nul

REM Compile pipeline modules (no Geyser deps)
echo [1/4] Compiling pipeline modules...
%JAVAC_EXE% -d build\classes -source 25 -target 25 ^
    src\main\java\autobridge\ModScanner.java ^
    src\main\java\autobridge\TexturePipeline.java ^
    src\main\java\autobridge\MappingBuilder.java ^
    src\main\java\autobridge\PackBuilder.java ^
    src\main\java\autobridge\AutoBlockDetector.java ^
    src\main\java\autobridge\CacheManager.java ^
    src\main\java\autobridge\GuiTranslator.java
if errorlevel 1 ( echo FAILED && exit /b 1 )

REM Compile AutoBridge (needs Geyser API)
echo [2/4] Compiling AutoBridge (Geyser Extension)...
%JAVAC_EXE% -cp "build\classes;libs\geyser-api.jar;libs\base-api.jar;libs\events.jar;libs\annotations.jar" ^
    -d build\classes -source 25 -target 25 ^
    src\main\java\autobridge\AutoBridge.java
if errorlevel 1 ( echo FAILED && exit /b 1 )

REM Compile tests
echo [3/4] Compiling tests...
%JAVAC_EXE% -cp build\classes -d build\classes -source 25 -target 25 ^
    src\test\java\autobridge\TestHarness.java ^
    src\test\java\autobridge\IntegrationTest.java
if errorlevel 1 ( echo FAILED && exit /b 1 )

REM Build extension JAR
echo [4/4] Building extension JAR...
xcopy /y /q /s build\classes\autobridge\*.* build\jar-staging\autobridge\ >nul
copy /y src\main\resources\extension.yml build\jar-staging\ >nul
%JAR_EXE% cf build\AutoBridge-0.1.0-SNAPSHOT.jar -C build\jar-staging .

echo.
echo === Build Complete ===
echo Extension JAR: build\AutoBridge-0.1.0-SNAPSHOT.jar
echo.
echo To test: java -cp build\classes autobridge.TestHarness
echo To run integration test: java -cp build\classes autobridge.IntegrationTest
echo.
echo To install: copy build\AutoBridge-0.1.0-SNAPSHOT.jar to Geyser's extensions/ folder
