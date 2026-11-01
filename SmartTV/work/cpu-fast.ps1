# Hold the TV's four cores on the "performance" governor (900 MHz always) or give them back to "ondemand".
#   .\work\cpu-fast.ps1 on      app cold starts about 25-35% faster (measured: VLC 3.5 s -> 2.2 s)
#   .\work\cpu-fast.ps1 off     stop the loop and restore the stock governor
#   .\work\cpu-fast.ps1 status
# An app cannot write the governor (SELinux) and a vendor daemon resets it to ondemand within about 20 s, so "on"
# starts work\cpu-hold.sh on the TV: a shell loop that re-asserts it every 5 s. It survives the adb connection
# closing, not a reboot or power cut: run "on" again after one.
param(
    [Parameter(Mandatory = $true)][ValidateSet('on', 'off', 'status')][string]$Mode,
    [string]$TvHost = $env:TV_HOST,
    [string]$TvMac = $env:TV_MAC,   # aa-bb-cc-dd-ee-ff; only used to find the TV by ARP when no host is given
    [string]$Adb = 'C:\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) {
    if (-not $TvMac) { throw "Pass -TvHost <ip> (or set TV_HOST), or give the TV's MAC with -TvMac / TV_MAC (aa-bb-cc-dd-ee-ff) so it can be found through ARP." }
    $mac = $TvMac.Replace(':', '-').ToLower()
    $TvHost = (arp -a | Select-String $mac | ForEach-Object { ($_ -split '\s+')[1] } | Select-Object -First 1)
    if (-not $TvHost) { throw "TV not found via ARP ($mac); pass -TvHost <ip>" }
}
$dev = "${TvHost}:5555"
& $Adb connect $dev | Out-Null
$stop = 'p=$(cat /data/local/tmp/cpu-hold.pid 2>/dev/null); [ -n "$p" ] && kill $p 2>/dev/null; rm -f /data/local/tmp/cpu-hold.pid'
switch ($Mode) {
    'on' {
        & $Adb -s $dev push (Join-Path $PSScriptRoot 'cpu-hold.sh') /data/local/tmp/cpu-hold.sh | Out-Null
        & $Adb -s $dev shell $stop
        & $Adb -s $dev shell 'setsid nohup sh /data/local/tmp/cpu-hold.sh >/dev/null 2>&1 &'
    }
    'off' {
        & $Adb -s $dev shell $stop
        & $Adb -s $dev shell 'for c in 0 1 2 3; do echo ondemand > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor; done'
    }
}
Start-Sleep -Seconds 1
"loop pid: " + (& $Adb -s $dev shell 'cat /data/local/tmp/cpu-hold.pid 2>/dev/null')
& $Adb -s $dev shell 'cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor'
