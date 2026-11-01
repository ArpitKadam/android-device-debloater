param([Parameter(Mandatory)][int]$Batch)
# Removes packages of the given batch for user 0 only (reversible via restore.ps1).
$dir = $PSScriptRoot
$log = Join-Path $dir 'applied.log'
Import-Csv (Join-Path $dir 'packages.csv') |
  Where-Object { $_.batch -eq "$Batch" -and $_.verdict -eq 'remove' } |
  ForEach-Object {
    $r = (adb shell pm uninstall -k --user 0 $_.package 2>&1 | Out-String).Trim()
    $line = "{0}`t{1}`t{2}" -f (Get-Date -Format s), $_.package, $r
    $line | Tee-Object -FilePath $log -Append
  }
# Android persists package state ~10 s after the last change; rebooting sooner loses it.
Start-Sleep 20
