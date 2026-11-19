# Builds out\HorizonHome.apk without Gradle. Run from anywhere.
# -Debuggable: lets `adb shell run-as home.horizon.tv` read the app's private files (for checking only;
#              reinstall a normal build afterwards, since it also lets ADB read your saved IPTV login).
param([switch]$Debuggable)
$here  = $PSScriptRoot
$root  = "$env:USERPROFILE\android-build"
$bt    = "$root\sdk\build-tools\33.0.2"
$jar   = "$root\sdk\platforms\android-28\android.jar"
$env:JAVA_HOME = "$root\jdk"
$env:Path = "$root\jdk\bin;$env:Path"
$out = "$here\out"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes","$out\dex" | Out-Null

& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
$dbg = @()
if ($Debuggable) { $dbg = @('--debug-mode') }
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$here\AndroidManifest.xml" @dbg `
    --min-sdk-version 24 --target-sdk-version 25 --version-code 1 --version-name 1.0 "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

# ExoPlayer (androidx.media3 1.4.1) and its support libraries, extracted from Google Maven AARs.
$libJars = Get-ChildItem "$root\libs\jars" -Filter *.jar | ForEach-Object FullName
$cp = $libJars -join ';'

$src = Get-ChildItem "$here\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -bootclasspath $jar -cp $cp -d "$out\classes" $src
if ($LASTEXITCODE) { throw 'javac failed' }

# R8: compiles to Android code AND drops library code nothing uses (see proguard-rules.pro: the video player is
# kept whole, so every stream format keeps working). Our classes are passed as one jar.
$appJar = "$out\app-classes.jar"
Push-Location "$out\classes"
& jar cf $appJar .
Pop-Location
if ($LASTEXITCODE) { throw 'jar failed' }
$progArgs = @($appJar) + $libJars
& java -Xmx2g -cp "$bt\lib\d8.jar" com.android.tools.r8.R8 --release --min-api 24 --lib $jar `
    --pg-conf "$here\proguard-rules.pro" --output "$out\dex" @progArgs
if ($LASTEXITCODE) { throw 'R8 failed' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$out\base.apk", 'Update')
foreach ($dx in Get-ChildItem "$out\dex" -Filter *.dex) {
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dx.FullName, $dx.Name)
}
$zip.Dispose()

& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign failed' }

# Local signing key, kept so future updates install over the top.
$ks = "$here\local-signing.keystore"
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias home `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Horizon Home'
}
& java -jar "$bt\lib\apksigner.jar" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\HorizonHome.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner failed' }
"Built: $out\HorizonHome.apk ($([math]::Round((Get-Item "$out\HorizonHome.apk").Length/1KB)) KB)"


