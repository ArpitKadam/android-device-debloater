# Rename TV-Wallpapers\* to w01, w02, ... (keeps the extension, logs old -> new so it can be undone) and write
# 1280x720 JPEG copies to work\theme\wallpapers\ for the launcher. Never opens an image visually; ffmpeg only scales.
$ErrorActionPreference = 'Stop'
$src = Join-Path $PSScriptRoot '..\TV-Wallpapers' | Resolve-Path
$out = Join-Path $PSScriptRoot 'theme\wallpapers'
$map = Join-Path $PSScriptRoot 'wallpaper-rename-map.txt'
$files = Get-ChildItem $src -File | Where-Object { $_.Name -notmatch '^w\d\d\.' } | Sort-Object Name
if ($files) {
    $i = (Get-ChildItem $src -File | Where-Object { $_.Name -match '^w\d\d\.' }).Count
    $log = foreach ($f in $files) {
        $i++; $new = 'w{0:00}{1}' -f $i, $f.Extension.ToLower()
        Rename-Item -LiteralPath $f.FullName -NewName $new
        "$($f.Name)`t$new"
    }
    Add-Content $map $log
}
New-Item -ItemType Directory -Force $out | Out-Null
Get-ChildItem $out -File | Remove-Item
foreach ($f in Get-ChildItem $src -File | Sort-Object Name) {
    $dst = Join-Path $out ($f.BaseName + '.jpg')
    & ffmpeg -loglevel error -y -i $f.FullName -vf 'scale=1280:720' -q:v 4 $dst
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed on $($f.Name)" }
}
$o = Get-ChildItem $out -File
"{0} files, {1:N1} MB total, largest {2:N0} KB" -f $o.Count, (($o | Measure-Object Length -Sum).Sum / 1MB), (($o | Measure-Object Length -Maximum).Maximum / 1KB)
