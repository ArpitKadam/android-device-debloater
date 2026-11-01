param([Parameter(Mandatory)][int]$Batch)
# Apply one batch, reboot, confirm boot. If boot fails but adb is alive, restore the batch's
# packages by retrying install-existing until the package service answers (see docs/DEBLOAT.md).
$dir = $PSScriptRoot
$pkgs = Import-Csv (Join-Path $dir 'packages.csv') |
  Where-Object { $_.batch -eq "$Batch" -and $_.verdict -eq 'remove' } | ForEach-Object package
& (Join-Path $dir 'apply-batch.ps1') -Batch $Batch

adb reboot; Start-Sleep 30
$deadline = (Get-Date).AddSeconds(240); $ok = $false
while ((Get-Date) -lt $deadline) {
  if ((adb devices) -match 'device$' -and ((adb shell getprop sys.boot_completed 2>$null) -match '1')) { $ok = $true; break }
  Start-Sleep 8
}
if ($ok) {
  Start-Sleep 20
  $inst = (adb shell pm list packages) -replace 'package:', ''
  $left = $pkgs | Where-Object { $inst -contains $_ }
  "batch $Batch OK; installed total $($inst.Count); still present: $($left -join ',')"
  return $true
}
"batch $Batch BOOT FAILED, rescuing"
$t = (Get-Date).AddSeconds(180); $todo = [System.Collections.ArrayList]@($pkgs)
while ((Get-Date) -lt $t -and $todo.Count -gt 0) {
  foreach ($p in @($todo)) {
    $o = (adb shell cmd package install-existing --user 0 $p 2>&1 | Out-String).Trim()
    if ($o -match 'installed for user') { $todo.Remove($p); "restored $p" }
  }
  Start-Sleep -Milliseconds 700
}
"unrestored: $($todo -join ',')"
return $false
