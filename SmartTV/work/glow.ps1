# Switch Home's colour glow on the TV without rebuilding: config only, then restart the launcher.
#   .\work\glow.ps1 none      no coloured wash at all (cinematic_art 0, ambient_glow off)
#   .\work\glow.ps1 dim       the wash at 18% instead of 50%
#   .\work\glow.ps1 default   the glass theme's own strength
# Does not touch work\theme\config.json; the choice is made permanent there once decided.
param(
    [Parameter(Mandatory = $true)][ValidateSet('none', 'dim', 'default')][string]$Variant,
    [string]$TvHost = $env:TV_HOST,
    [string]$TvMac = $env:TV_MAC   # aa-bb-cc-dd-ee-ff; only used to find the TV by ARP when no host is given
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$py = Join-Path $root '..\tools\venv\Scripts\python.exe'
$tv = Join-Path $root '..\tools\tv.py'
if (-not $TvHost) {
    if (-not $TvMac) { throw "Pass -TvHost <ip> (or set TV_HOST), or give the TV's MAC with -TvMac / TV_MAC (aa-bb-cc-dd-ee-ff) so it can be found through ARP." }
    $mac = $TvMac.Replace(':', '-').ToLower()
    $TvHost = (arp -a | Select-String $mac | ForEach-Object { ($_ -split '\s+')[1] } | Select-Object -First 1)
    if (-not $TvHost) { throw "TV not found via ARP ($mac); pass -TvHost <ip>" }
}
$env:TV_HOST = $TvHost
$cfg = Get-Content (Join-Path $root 'theme\config.json') -Raw | ConvertFrom-Json
foreach ($k in 'cinematic_art', 'ambient_glow') { $cfg.PSObject.Properties.Remove($k) }
switch ($Variant) {
    'none' { $cfg | Add-Member cinematic_art 0 -Force; $cfg | Add-Member ambient_glow $false -Force }
    'dim' { $cfg | Add-Member cinematic_art 18 -Force }
}
$tmp = Join-Path $env:TEMP 'glow-config.json'
[IO.File]::WriteAllText($tmp, ($cfg | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding $false))
& $py -I $tv --push $tmp /data/local/tmp/glow.json | Out-Null
$restart = @'
cp -f /data/local/tmp/glow.json /sdcard/Android/data/com.gathin.tvhome/files/config.json; rm -f /data/local/tmp/glow.json; am force-stop com.gathin.tvhome; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity | grep Status
'@
$restart.Trim() | & $py -I $tv --batch | Select-Object -Last 1
Write-Host "glow: $Variant (TV $TvHost)"
