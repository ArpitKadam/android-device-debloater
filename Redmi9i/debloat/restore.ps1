param([string[]]$Package)
# Brings removed packages back for user 0. No args = every package in applied.log that reported Success.
$dir = $PSScriptRoot
if (-not $Package) {
  $Package = Get-Content (Join-Path $dir 'applied.log') |
    Where-Object { $_ -match "`tSuccess$" } |
    ForEach-Object { ($_ -split "`t")[1] } | Sort-Object -Unique
}
foreach ($p in $Package) { "{0}`t{1}" -f $p, (adb shell cmd package install-existing --user 0 $p 2>&1 | Out-String).Trim() }
