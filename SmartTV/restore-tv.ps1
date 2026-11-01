# One-line undo for the SmartTV debloat.
#   .\restore-tv.ps1             put the TV back (finds its IP itself)
#   .\restore-tv.ps1 -DryRun     print the plan, change nothing
# For every batch in work\batches (newest first) that has a recovery record: replays the state disable.ps1
# recorded (packages, Home, animation scales) via the repo's restore.ps1. Then reinstalls the apps removed
# with pm uninstall from backup\apks, and puts Private DNS back as backed up.
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$TvMac = $env:TV_MAC,   # the TV's MAC (aa-bb-cc-dd-ee-ff); its IP changes with every network
    [string[]]$List = @(Get-ChildItem (Join-Path $PSScriptRoot 'work\batches\*.txt') | Sort-Object Name -Descending | ForEach-Object FullName),
    [string]$Stock = 'com.google.android.tvlauncher',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot   # tools\ lives next to this script
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$adb = 'C:\platform-tools\adb.exe'
$backup = Join-Path $PSScriptRoot 'backup'

function Test-Adb($ip) {
    $c = New-Object Net.Sockets.TcpClient
    try { return $c.ConnectAsync($ip, 5555).Wait(2000) } catch { return $false } finally { $c.Close() }
}
if (-not $TvHost) {
    if (-not $TvMac) { throw "Pass -TvHost <ip> (or set TV_HOST), or give the TV's MAC with -TvMac / TV_MAC (aa-bb-cc-dd-ee-ff) so it can be found through ARP." }
    $mac = $TvMac.Replace(':', '-').ToLower()
    $ip = (arp -a | Select-String $mac | ForEach-Object { ($_ -split '\s+')[1] } | Select-Object -First 1)
    if ($ip -and (Test-Adb $ip)) { $TvHost = $ip } else { throw "TV not found via ARP ($mac). Pass -TvHost <ip> (TV on, same Wi-Fi)." }
}
$env:TV_HOST = $TvHost
if (-not $List) { throw 'no debloat lists found (work\batches\*.txt); pass -List <file>' }
Write-Host "TV: $TvHost   lists: $($List.Count)"

# The repo keys its record by TV IP and the IP changes per network: if a list has none for this IP, copy the
# newest record of the same list from another address so restore replays the real original state.
# Prints 'record' when a record exists for this list and IP afterwards, 'none' otherwise.
$adopt = @'
import sys, shutil
from pathlib import Path
sys.path.insert(0, sys.argv[1] + r'\tools')
import recovery
lst, host = Path(sys.argv[2]), sys.argv[3]
mine = recovery.record_path(lst, host)
if not mine.exists():
    others = [p for _, p in recovery.records_under_other_addresses(lst, mine)]
    if others:
        src = max(others, key=lambda p: p.stat().st_mtime)
        shutil.copy2(src, mine)
        print('adopted recovery record', src.name, '->', mine.name, file=sys.stderr)
print('record' if mine.exists() else 'none')
'@
$todo = @()
foreach ($l in $List) {
    $l = (Resolve-Path $l).Path
    $state = ($adopt | & $py - $repo $l $TvHost | Select-Object -Last 1)
    if ($state -eq 'record') { $todo += $l } else { Write-Host "skip (never applied, no record): $(Split-Path $l -Leaf)" }
}

$apps = Get-ChildItem (Join-Path $backup 'apks') -Directory -ErrorAction SilentlyContinue | Where-Object { Get-ChildItem $_.FullName -Filter *.apk }
$dns = @{}
foreach ($k in 'private_dns_mode', 'private_dns_specifier') {
    $m = Select-String -Path (Join-Path $backup 'settings_global.txt') -Pattern "^$k=(.*)$" | Select-Object -First 1
    $dns[$k] = if ($m) { $m.Matches[0].Groups[1].Value.Trim() } else { $null }
}

if ($DryRun) {
    foreach ($l in $todo) {
        Write-Host "--- plan for $(Split-Path $l -Leaf) (recovery.py) ---"
        & $py -I (Join-Path $repo 'tools\recovery.py') --commands $l $Stock
    }
    Write-Host '--- reinstall if missing ---'; $apps | ForEach-Object { $_.Name }
    Write-Host '--- CPU governor: keep-alive loop stopped, ondemand restored ---'
    Write-Host '--- Private DNS back to ---'; $dns.GetEnumerator() | ForEach-Object { "$($_.Key) = $(if ($null -eq $_.Value) { '(unset)' } else { $_.Value })" }
    return
}

foreach ($l in $todo) {
    Write-Host "=== restoring $(Split-Path $l -Leaf)"
    & (Join-Path $repo 'tools\restore.ps1') -TvHost $TvHost $l $Stock
}
& $adb connect "${TvHost}:5555" | Out-Null
foreach ($a in $apps) {
    $have = & $adb -s "${TvHost}:5555" shell pm list packages $a.Name
    if ($have -match [regex]::Escape($a.Name)) { Write-Host "$($a.Name): already installed"; continue }
    $apks = (Get-ChildItem $a.FullName -Filter *.apk).FullName
    Write-Host "$($a.Name): reinstalling"
    & $adb -s "${TvHost}:5555" install-multiple -r @apks
}
foreach ($k in $dns.Keys) {
    $v = $dns[$k]
    if ($null -eq $v) { & $adb -s "${TvHost}:5555" shell settings delete global $k | Out-Null }
    else { & $adb -s "${TvHost}:5555" shell settings put global $k $v | Out-Null }
}
# The performance-governor loop (gone at reboot anyway): stop it and give the cores back to ondemand.
& (Join-Path $PSScriptRoot 'work\cpu-fast.ps1') off -TvHost $TvHost | Out-Null
# Accessibility helper back to the backed-up state (empty list, disabled); the launcher's HomeKeyService is on after T6.
& $adb -s "${TvHost}:5555" shell 'settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0' | Out-Null
Write-Host 'Restore done. Reboot the TV once and check Home.'
