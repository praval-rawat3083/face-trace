# Ask for a local MySQL administrator account without saving its password.
$mysqlPath = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path $mysqlPath)) {
    throw "MySQL client was not found at $mysqlPath. Install MySQL Server 8.0 or edit this script's path."
}

Write-Host 'Run this script by itself. At the next prompt, enter only your MySQL administrator username.'
$adminUser = Read-Host 'MySQL administrator username only'
if ([string]::IsNullOrWhiteSpace($adminUser) -or $adminUser -match '\s|[\\/]') {
    throw 'Enter only your MySQL username here; do not enter another PowerShell command.'
}
$createSql = Get-Content -Raw (Join-Path $PSScriptRoot 'create_database.sql')
$schemaSql = Get-Content -Raw (Join-Path $PSScriptRoot 'schema.sql')

# Build both database schemas from the checked-in SQL files.
$sqlBatch = $createSql + "`nUSE facetrace_test;`n" + $schemaSql
$sqlBatch += "`nUSE facetrace;`n" + $schemaSql

# The MySQL client asks for the password in the terminal, not from this file.
& $mysqlPath "--user=$adminUser" --password "--execute=$sqlBatch"
if ($LASTEXITCODE -ne 0) {
    throw 'MySQL did not create the databases. Check the username and password, then retry.'
}
Write-Host 'The facetrace and facetrace_test databases and tables are ready.'
