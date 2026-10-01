param(
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$Serial,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runDir = Join-Path $repo ('app/build/regression/' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $runDir 'classes'
$dex = Join-Path $runDir 'dex'
[IO.Directory]::CreateDirectory($classes) | Out-Null
[IO.Directory]::CreateDirectory($dex) | Out-Null
$androidJar = Join-Path $Sdk 'platforms/android-36/android.jar'
$buildTools = Join-Path $Sdk 'build-tools/36.0.0'
$env:JAVA_HOME = $JavaHome
$javaSources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$production = @(
    'Configs/ConfigItem.java', 'Configs/ConfigPreferences.java', 'Database/MessageDatabase.java',
    'messages/MessageStorage.java', 'utils/MessageIdParser.java',
    'features/ghostMode/ReadRequestPermits.java', 'calendar/CalendarDate.java',
    'calendar/ConverterCalendar.java', 'language/Keys.java',
    'hooks/HookInstallation.java', 'hooks/HMethod.java', 'compat/XposedHelpers.java',
    'compat/XC_MethodHook.java', 'base/BaseMethodHook.java', 'Class/ClassNames.java',
    'features/ghostMode/PhoneDisplayMask.java', 'features/otherFeatures/FeatureStateManager.java',
    'features/ui/HijriDate.java', 'features/ui/DisableChannelSwipeBack.java', 'virtuals/ui/ChatActivity.java',
    'settings/TelegramSettingsCompat.java', 'settings/Android16Switch.java'
)
foreach ($source in $production) { $javaSources += Join-Path $repo ('app/src/main/java/com/my/televip/' + $source) }
$sourceList = Join-Path $runDir 'sources.txt'
[IO.File]::WriteAllLines($sourceList, @($javaSources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
$jar = Join-Path $runDir 'classes.jar'
[IO.Compression.ZipFile]::CreateFromDirectory($classes, $jar)
& (Join-Path $buildTools 'd8.bat') --min-api 27 --lib $androidJar --output $dex $jar
if ($LASTEXITCODE -ne 0) { throw 'Regression DEX failed' }
$unsigned = Join-Path $runDir 'unsigned.apk'
& (Join-Path $buildTools 'aapt2.exe') link -I $androidJar --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -o $unsigned
if ($LASTEXITCODE -ne 0) { throw 'Regression package failed' }
$archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null }
finally { $archive.Dispose() }
$aligned = Join-Path $runDir 'aligned.apk'
& (Join-Path $buildTools 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'Regression alignment failed' }
$key = Join-Path $runDir 'test-only.p12'
& (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -keystore $key -storetype PKCS12 -storepass android -keypass android -alias regression -dname 'CN=TeleVip Regression Only' -keyalg RSA -keysize 2048 -validity 30
if ($LASTEXITCODE -ne 0) { throw 'Regression test key failed' }
$apk = Join-Path $runDir 'regression.apk'
& (Join-Path $buildTools 'apksigner.bat') sign --ks $key --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'Regression signing failed' }
Write-Output "Regression APK: $apk"
if ($Serial) {
    & $Adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Regression installation failed' }
    try {
        $result = & $Adb -s $Serial shell am instrument -w org.televip.regression/.RegressionInstrumentation
        $result | Set-Content (Join-Path $runDir 'result.txt')
        $result | Write-Output
        if (-not (($result -join [Environment]::NewLine) -match 'TELEVIP_REGRESSION_PASS: 9 regression groups passed;')) { throw 'Device regressions failed' }
    } finally { & $Adb -s $Serial uninstall org.televip.regression }
}
