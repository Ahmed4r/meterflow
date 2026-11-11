$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (!(Test-Path .env)) { throw 'Copy .env.example to .env and set unique secrets first.' }
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$projectJdk = Get-ChildItem .tools -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($projectJdk) { $env:JAVA_HOME = $projectJdk.FullName }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
& $java -jar backend/target/meterflow-0.1.0.jar
