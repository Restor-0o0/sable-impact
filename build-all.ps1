<#
.SYNOPSIS
    Builds every release variant of the mod in one run and collects the jars in one folder.

.DESCRIPTION
    The variants live on separate branches rather than in one tree, so each is built from a throwaway
    git worktree: nothing is checked out over your working copy, the branch you are on does not matter,
    and an interrupted run cannot leave you on the wrong branch.

    Because a worktree holds committed state, uncommitted changes are NOT built. The script says so
    rather than quietly shipping a jar that is missing your last edit.

.PARAMETER OutDir
    Where the jars are collected. Default: dist

.PARAMETER Offline
    Passes --offline to Gradle. Only works once each variant has been built online at least once.

.PARAMETER SkipTests
    Builds without running the test suite. Faster, and worth exactly what it costs.

.EXAMPLE
    .\build-all.ps1

.EXAMPLE
    .\build-all.ps1 -Offline -OutDir C:\temp\jars
#>
[CmdletBinding()]
param(
    [string]$OutDir = 'dist',
    [switch]$Offline,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

# One line per shipped jar. A version bump is a rename here and nowhere else.
$Branches = @('v1.0.0', 'v1.0.0-fast')

$repo = $PSScriptRoot
if (-not (Test-Path (Join-Path $repo 'gradlew.bat'))) {
    throw "No gradlew.bat next to this script - run it from the repository root."
}

function Read-ModsToml {
    param([string]$JarPath)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $zip.GetEntry('META-INF/neoforge.mods.toml')
        if ($null -eq $entry) { return $null }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}

function Get-TomlValue {
    param([string]$Toml, [string]$Key)
    $m = [regex]::Match($Toml, "(?m)^\s*$Key\s*=\s*`"([^`"]*)`"")
    if ($m.Success) { return $m.Groups[1].Value }
    return '?'
}

$dirty = git -C $repo status --porcelain -- . | Where-Object { $_ }
if ($dirty) {
    Write-Host ''
    Write-Host 'Uncommitted changes are present. Variants are built from the committed branches,' -ForegroundColor Yellow
    Write-Host 'so anything below is NOT in the jars:' -ForegroundColor Yellow
    $dirty | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}

$out = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repo $OutDir }
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }

$gradleArgs = @('build')
if ($Offline) { $gradleArgs += '--offline' }
if ($SkipTests) { $gradleArgs += '-x'; $gradleArgs += 'test' }

$built = @()
$started = Get-Date

foreach ($branch in $Branches) {
    Write-Host ''
    Write-Host "=== $branch ===" -ForegroundColor Cyan

    if (-not (git -C $repo rev-parse --verify --quiet "$branch^{commit}")) {
        throw "No such branch: $branch"
    }

    $tree = Join-Path ([System.IO.Path]::GetTempPath()) ("cai-build-" + ($branch -replace '[^A-Za-z0-9._-]', '_'))
    if (Test-Path $tree) { git -C $repo worktree remove --force $tree }
    git -C $repo worktree prune

    # Detached, so a branch that is already checked out in the main tree can still be built.
    git -C $repo worktree add --detach --quiet $tree $branch
    if ($LASTEXITCODE -ne 0) { throw "Could not create a worktree for $branch." }

    try {
        Push-Location $tree
        try {
            & (Join-Path $tree 'gradlew.bat') @gradleArgs
            if ($LASTEXITCODE -ne 0) { throw "Gradle failed on $branch (exit $LASTEXITCODE)." }
        } finally { Pop-Location }

        $jars = Get-ChildItem (Join-Path $tree 'build\libs\*.jar') -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' }
        if (-not $jars) { throw "$branch built but produced no jar." }

        foreach ($jar in $jars) {
            Copy-Item $jar.FullName (Join-Path $out $jar.Name) -Force
            $toml = Read-ModsToml $jar.FullName
            $built += [pscustomobject]@{
                Jar     = $jar.Name
                Name    = if ($toml) { Get-TomlValue $toml 'displayName' } else { '?' }
                Version = if ($toml) { Get-TomlValue $toml 'version' } else { '?' }
                License = if ($toml) { Get-TomlValue $toml 'license' } else { '?' }
                KB      = [math]::Round($jar.Length / 1KB)
            }
        }
    } finally {
        if (Test-Path $tree) { git -C $repo worktree remove --force $tree }
        git -C $repo worktree prune
    }
}

Write-Host ''
Write-Host ("Done in {0:mm\:ss}. Jars are in $out" -f ((Get-Date) - $started)) -ForegroundColor Green
$built | Format-Table -AutoSize

$clashes = $built | Group-Object Version | Where-Object { $_.Count -gt 1 }
if ($clashes) {
    Write-Host 'These share a mod version, so only one of them can be installed at a time.' -ForegroundColor DarkGray
}
