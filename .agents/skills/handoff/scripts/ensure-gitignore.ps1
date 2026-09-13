<#
.SYNOPSIS
    Ensure HANDOFF.md is listed in .gitignore at the given directory (default: current directory).
    Idempotent: safe to run multiple times, never duplicates the entry.

.PARAMETER TargetDir
    The directory containing (or that should contain) the .gitignore file. Defaults to the current directory.

.EXAMPLE
    ./ensure-gitignore.ps1
    ./ensure-gitignore.ps1 -TargetDir "C:\path\to\project"

.NOTES
    Exit codes:
      0 - success (created .gitignore, appended entry, or entry already present)
      1 - target directory does not exist
#>

param(
    [string]$TargetDir = "."
)

$Entry = "HANDOFF.md"
$GitignorePath = Join-Path $TargetDir ".gitignore"

if (-not (Test-Path -Path $TargetDir -PathType Container)) {
    Write-Error "Directory not found: $TargetDir"
    exit 1
}

if (-not (Test-Path -Path $GitignorePath -PathType Leaf)) {
    Set-Content -Path $GitignorePath -Value $Entry
    Write-Output "Created $GitignorePath and added '$Entry'."
    exit 0
}

# Exact, whole-line match — avoids false positives from substrings or patterns like *.md
$existingLines = Get-Content -Path $GitignorePath -ErrorAction SilentlyContinue
if ($existingLines -contains $Entry) {
    Write-Output "'$Entry' is already present in $GitignorePath - no change made."
    exit 0
}

Add-Content -Path $GitignorePath -Value $Entry
Write-Output "Appended '$Entry' to $GitignorePath."
