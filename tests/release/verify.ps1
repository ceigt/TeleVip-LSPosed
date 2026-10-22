param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$CertificateSha256 = '60f2274c49aac137bd0cc6a30b522f8930c95fe19c53ba7c2469a939665a2efc',
    [int]$ExpectedVersionCode = 0,
    [string]$ExpectedVersionName
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$buildTools = Join-Path $Sdk 'build-tools/36.0.0'
$windowsTools = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$signer = Join-Path $buildTools $(if ($windowsTools) { 'apksigner.bat' } else { 'apksigner' })
$aligner = Join-Path $buildTools $(if ($windowsTools) { 'zipalign.exe' } else { 'zipalign' })
$aapt = Join-Path $buildTools $(if ($windowsTools) { 'aapt2.exe' } else { 'aapt2' })
$signature = & $signer verify --verbose --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
if (-not (($signature -join "`n") -match ('certificate SHA-256 digest: ' + [regex]::Escape($CertificateSha256)))) { throw 'Unexpected release signing identity' }
& $aligner -c 4 $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed' }
$manifest = & $aapt dump badging $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest read failed' }
if (-not (($manifest -join "`n") -match "package: name='org.televip'")) { throw 'Unexpected package identity' }
if ($ExpectedVersionCode -gt 0 -and -not (($manifest -join "`n") -match ("versionCode='" + $ExpectedVersionCode + "'"))) { throw 'Unexpected version code' }
if ($ExpectedVersionName -and -not (($manifest -join "`n") -match ("versionName='" + [regex]::Escape($ExpectedVersionName) + "'"))) { throw 'Unexpected version name' }
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk).Path)
try {
    $expected = @{
        'META-INF/xposed/java_init.list' = 'com.my.televip.ModernModule'
        'META-INF/xposed/module.prop' = 'minApiVersion=102'
    }
    foreach ($item in $expected.GetEnumerator()) {
        $entry = $archive.GetEntry($item.Key)
        if (-not $entry) { throw ('Missing ' + $item.Key) }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if (-not $content.Contains($item.Value)) { throw ('Invalid ' + $item.Key) }
        if ($item.Key.EndsWith('module.prop') -and -not $content.Contains('targetApiVersion=102')) { throw 'Unexpected target API' }
    }
    if (-not $archive.GetEntry('classes.dex')) { throw 'Missing module DEX' }
} finally { $archive.Dispose() }
Write-Output ('RELEASE_APK_CHECK_PASS: org.televip, API 102, signature and alignment; SHA256=' + (Get-FileHash -Algorithm SHA256 $Apk).Hash.ToLowerInvariant())
