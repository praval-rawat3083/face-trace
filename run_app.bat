@echo off
title FaceTrace - Smart Attendance System
cd /d "%~dp0"
echo ========================================================
echo  FaceTrace Smart Attendance System - Launcher
echo ========================================================

echo [1/3] Syncing resources to target...
xcopy /Y /Q "src\main\resources\facetrace\view\*.css"  "target\classes\facetrace\view\" >nul 2>&1
xcopy /Y /Q "src\main\resources\facetrace\view\*.fxml" "target\classes\facetrace\view\" >nul 2>&1
xcopy /Y /Q "config\database.properties" "target\classes\" >nul 2>&1
echo       Done.

echo [2/3] Building sources...
"C:\Program Files\Java\jdk-22\bin\javac.exe" @.work\cp.txt @.work\sources.txt -d target\classes -encoding UTF-8
if errorlevel 1 (
    echo BUILD FAILED. Check errors above.
    pause
    exit /b 1
)
echo       Done.

echo [3/3] Launching application...
"C:\Program Files\Java\jdk-22\bin\java.exe" ^
  --module-path "%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\17.0.10\javafx-controls-17.0.10-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\17.0.10\javafx-base-17.0.10-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\17.0.10\javafx-graphics-17.0.10-win.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\17.0.10\javafx-fxml-17.0.10-win.jar" ^
  --add-modules javafx.controls,javafx.fxml,javafx.graphics ^
  -cp "target\classes;.work\libs\*" ^
  facetrace.FaceTraceApp
pause
