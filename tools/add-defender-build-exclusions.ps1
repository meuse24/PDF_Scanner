Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$paths = @(
    $repoRoot,
    "$env:USERPROFILE\.gradle",
    "$env:LOCALAPPDATA\Android\Sdk",
    "$env:LOCALAPPDATA\Google\AndroidStudio2025.3.4"
) | Where-Object { Test-Path $_ } | Sort-Object -Unique

$processes = @(
    "$env:JAVA_HOME\bin\java.exe",
    "$env:JAVA_HOME\bin\javaw.exe",
    "$env:ProgramFiles\Android\Android Studio\bin\studio64.exe"
) | Where-Object { Test-Path $_ } | Sort-Object -Unique

if (-not ([bool](net session 2>$null))) {
    throw "Run this script from an elevated PowerShell session."
}

foreach ($path in $paths) {
    Add-MpPreference -ExclusionPath $path
}

foreach ($process in $processes) {
    Add-MpPreference -ExclusionProcess $process
}

Write-Host "Added Defender exclusions:"
$paths | ForEach-Object { Write-Host "Path: $_" }
$processes | ForEach-Object { Write-Host "Process: $_" }
