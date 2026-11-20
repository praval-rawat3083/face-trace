@echo off
title FaceTrace - Smart Attendance System (Maven)
cd /d "%~dp0"
set MAVEN_HOME=C:\Users\prati\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0
set PATH=%MAVEN_HOME%\bin;%PATH%
echo ========================================================
echo  FaceTrace Smart Attendance System  -  Maven Launcher
echo ========================================================
echo Syncing resources...
xcopy /Y /Q "src\main\resources\facetrace\view\*.css"  "target\classes\facetrace\view\" >nul 2>&1
xcopy /Y /Q "src\main\resources\facetrace\view\*.fxml" "target\classes\facetrace\view\" >nul 2>&1
mvn compile javafx:run
pause
