$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
Push-Location $projectRoot
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting FaceTrace - Smart Attendance System" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
mvn compile javafx:run
Pop-Location
