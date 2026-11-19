# Builds "Horizon TV Setup.exe" (one file, nothing to install) into setup-app\dist.
# Bundles adb (from %USERPROFILE%\platform-tools) and the latest Horizon Home (horizon-home\out\HorizonHome.apk).
#   powershell -ExecutionPolicy Bypass -File setup-app\build_exe.ps1
$here = $PSScriptRoot
$assets = Join-Path $here 'assets'
New-Item -ItemType Directory -Force (Join-Path $assets 'adb') | Out-Null

$pt = "$env:USERPROFILE\platform-tools"
foreach ($f in 'adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll') {
    if (Test-Path "$pt\$f") { Copy-Item "$pt\$f" (Join-Path $assets 'adb') -Force }
}
$apk = Join-Path $here '..\horizon-home\out\HorizonHome.apk'
if (-not (Test-Path $apk)) { throw "Build Horizon Home first: horizon-home\build.ps1" }
Copy-Item $apk (Join-Path $assets 'HorizonHome.apk') -Force

Push-Location $here
try {
    # Leave out libraries the app never uses (they're only pulled in because other installed packages
    # might import them): numpy (~27 MB), SSL/crypto (~8 MB), Pillow's AVIF plugin (~8 MB) and smaller ones.
    $exclude = @('numpy', 'yaml', 'charset_normalizer', 'requests', 'urllib3', 'certifi', 'idna', 'ssl', '_ssl',
                 '_hashlib', 'PIL._avif', 'PIL.AvifImagePlugin', 'PIL._imagingcms', 'PIL.ImageCms', '_zstd',
                 'compression.zstd', '_decimal', 'decimal', 'unittest', 'pydoc', 'doctest', 'pdb', 'xmlrpc',
                 'http.server', 'email', 'setuptools', 'pkg_resources', 'distutils', 'lib2to3', 'tkinter.test',
                 'test', 'sqlite3', '_sqlite3', 'lzma', '_lzma', 'bz2', '_bz2', 'pyexpat', 'xml')
    $exArgs = @()
    foreach ($m in $exclude) { $exArgs += '--exclude-module'; $exArgs += $m }
    python -m PyInstaller --noconfirm --clean --onefile --windowed --name 'Horizon TV Setup' @exArgs `
        --add-data "${assets};assets" --distpath dist --workpath build --specpath build horizon_setup.py
    if ($LASTEXITCODE) { throw 'PyInstaller failed' }
} finally { Pop-Location }
$exe = Join-Path $here 'dist\Horizon TV Setup.exe'
"Built: $exe ($([math]::Round((Get-Item $exe).Length / 1MB, 1)) MB)"

# Self-test inside the packaged app: wallpapers, icons, image formats, languages and adb all still work
# with the unused modules left out.
$report = Join-Path $env:TEMP 'horizon-selftest.txt'
Remove-Item $report -ErrorAction SilentlyContinue
Start-Process -FilePath $exe -ArgumentList '--selftest', "`"$report`"" -Wait
$result = Get-Content $report -ErrorAction SilentlyContinue
$result | ForEach-Object { "  self-test: $_" }
if (-not ($result -contains 'OK')) { throw 'Self-test FAILED: see above' }
