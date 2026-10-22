[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Destination
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path -LiteralPath $Source).Path.TrimEnd('\')
$destinationRoot = [IO.Path]::GetFullPath($Destination).TrimEnd('\')
$bazelExternal = Join-Path $root 'bazel-external'
$linkTargets = @{}
$materialized = 0
$cacheLinks = 0

function IsUnder([string]$Path, [string]$Directory) {
    return $Path.StartsWith($Directory + '\', [StringComparison]::OrdinalIgnoreCase)
}

function GetLinkText([IO.FileSystemInfo]$Item) {
    $output = @(& fsutil reparsepoint query $Item.FullName 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect reparse point: $($Item.FullName)" }
    $tag = [regex]::Match(($output -join "`n"), '0x[a-fA-F0-9]{8}').Value.ToLowerInvariant()
    if ($tag -eq '0xa000000c') {
        $targets = @($Item.Target)
        if ($targets.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$targets[0])) {
            throw "Unresolved Windows symbolic link: $($Item.FullName)"
        }
        return [string]$targets[0]
    }
    if ($tag -ne '0xa000001d') { throw "Unsupported reparse point ($tag): $($Item.FullName)" }

    # WSL writes UTF-8 link text after a four-byte header. PowerShell's Target is empty for this tag.
    $bytes = [Collections.Generic.List[byte]]::new()
    foreach ($line in $output) {
        if ($line -notmatch '^[0-9a-fA-F]{4}:') { continue }
        $hex = $line.Substring(7, [Math]::Min(49, $line.Length - 7))
        foreach ($match in [regex]::Matches($hex, '(?i)(?<![0-9a-f])[0-9a-f]{2}(?![0-9a-f])')) {
            $bytes.Add([Convert]::ToByte($match.Value, 16))
        }
    }
    if ($bytes.Count -lt 5 -or $bytes[0] -ne 2 -or $bytes[1] -ne 0 -or
        $bytes[2] -ne 0 -or $bytes[3] -ne 0) {
        throw "Invalid WSL symbolic link: $($Item.FullName)"
    }
    return ([Text.UTF8Encoding]::new($false, $true)).GetString($bytes.ToArray(), 4, $bytes.Count - 4)
}

function GetSafeTarget([IO.FileSystemInfo]$Item) {
    if ($linkTargets.ContainsKey($Item.FullName)) { return $linkTargets[$Item.FullName] }
    $text = GetLinkText $Item
    if ([string]::IsNullOrWhiteSpace($text) -or $text -match '^[\\/]|^[A-Za-z]:' -or
        $text.Contains(':') -or $text.Contains([char]0)) {
        throw "Absolute or invalid vendor link: $($Item.FullName) -> $text"
    }
    $target = [IO.Path]::GetFullPath((Join-Path (Split-Path $Item.FullName -Parent) $text.Replace('/', '\')))
    if (-not (IsUnder $target $root) -or $target -eq $bazelExternal -or
        (IsUnder $target $bazelExternal)) {
        throw "Vendor link escapes source or references Bazel cache: $($Item.FullName) -> $text"
    }
    $parent = Split-Path $target -Parent
    while ($parent -ne $root) {
        if (-not (IsUnder $parent $root)) { throw "Vendor link parent escapes source: $($Item.FullName)" }
        $parentItem = Get-Item -LiteralPath $parent -Force -ErrorAction Stop
        if ($parentItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Vendor link traverses another directory link: $($Item.FullName)"
        }
        $parent = Split-Path $parent -Parent
    }
    $null = Get-Item -LiteralPath $target -Force -ErrorAction Stop
    $linkTargets[$Item.FullName] = $target
    return $target
}

function CopyNode([string]$From, [string]$To, [string[]]$Stack) {
    $item = Get-Item -LiteralPath $From -Force -ErrorAction Stop
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        if ($Stack -contains $item.FullName) { throw "Vendor link cycle: $($item.FullName)" }
        $target = GetSafeTarget $item
        $script:materialized++
        CopyNode $target $To ($Stack + $item.FullName)
        return
    }
    if ($item -is [IO.DirectoryInfo]) {
        New-Item -ItemType Directory -Path $To -Force | Out-Null
        foreach ($child in Get-ChildItem -LiteralPath $item.FullName -Force) {
            CopyNode $child.FullName (Join-Path $To $child.Name) $Stack
        }
    } else {
        Copy-Item -LiteralPath $item.FullName -Destination $To -Force
    }
}

if (Test-Path -LiteralPath $destinationRoot) { throw "Destination already exists: $destinationRoot" }
$allLinks = @(Get-ChildItem -LiteralPath $root -Recurse -Force -Attributes ReparsePoint)
foreach ($link in $allLinks) {
    if ($link.FullName -eq $bazelExternal) {
        $cacheLinks++
        $cacheTarget = GetLinkText $link
        if ($cacheTarget -notmatch '^/(?:[A-Za-z0-9._-]+/)+external$' -or
            $cacheTarget -match '(^|/)\.\.(/|$)') {
            throw "Unexpected Bazel cache link: $($link.FullName) -> $cacheTarget"
        }
        continue
    }
    $null = GetSafeTarget $link
}

New-Item -ItemType Directory -Path $destinationRoot | Out-Null
foreach ($item in Get-ChildItem -LiteralPath $root -Force) {
    if ($item.FullName -eq $bazelExternal) { continue }
    CopyNode $item.FullName (Join-Path $destinationRoot $item.Name) @()
}
if (@(Get-ChildItem -LiteralPath $destinationRoot -Recurse -Force -Attributes ReparsePoint).Count -ne 0) {
    throw 'Materialized vendor still contains reparse points.'
}
Write-Host "Validated $($allLinks.Count - $cacheLinks) internal vendor links ($materialized copy resolutions); excluded $cacheLinks generated bazel-external cache link."
