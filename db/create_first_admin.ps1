# Launch the one-time Java prompt; the password is typed privately in the console.
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    Write-Host 'Create the first FaceTrace admin. The password will not appear on screen.'
    & mvn -q compile exec:java '-Dexec.mainClass=facetrace.app.CreateFirstAdmin'
    if ($LASTEXITCODE -ne 0) {
        throw 'First-admin setup failed. Check the database tables and local connection settings.'
    }
} finally {
    Pop-Location
}
