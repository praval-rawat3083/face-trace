@echo off
title FaceTrace - Smart Attendance System
cd /d "%~dp0"
echo ========================================================
echo Starting FaceTrace - Smart Attendance System
echo ========================================================
mvn compile javafx:run
pause
