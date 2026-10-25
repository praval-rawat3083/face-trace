# Launch the admin password reset prompt from the project folder.
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    Write-Host 'Reset one existing ADMIN password. The reset prompt will show typed text as requested.'
    Write-Warning 'Use this only when nobody else can see your screen.'
    & mvn -q compile exec:java '-Dexec.mainClass=facetrace.app.ResetAdminPassword'
    if ($LASTEXITCODE -ne 0) {
        throw 'Password reset failed. Check the admin username, database settings, and error above.'
    }
} finally {
    Pop-Location
}
