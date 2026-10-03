param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$CertificateSha256 = '60f2274c49aac137bd0cc6a30b522f8930c95fe19c53ba7c2469a939665a2efc'
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$buildTools = Join-Path $Sdk 'build-tools/36.0.0'
$signature = & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
if (-not (($signature -join "`n") -match ('certificate SHA-256 digest: ' + [regex]::Escape($CertificateSha256)))) { throw 'Unexpected release signing identity' }
& (Join-Path $buildTools 'zipalign.exe') -c 4 $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed' }
$manifest = & (Join-Path $buildTools 'aapt2.exe') dump badging $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest read failed' }
if (-not (($manifest -join "`n") -match "package: name='org.televip'")) { throw 'Unexpected package identity' }
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
