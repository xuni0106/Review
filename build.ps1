# 片刻 Pianji - 零依赖本地 APK 构建脚本
# 使用本机 Android SDK Build-Tools + JDK，无需 Gradle/AndroidX。
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$jbr = "C:\Program Files\Android\Android Studio\jbr"
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$buildToolsVersion = "34.0.0"
$bt = Join-Path $sdk "build-tools\$buildToolsVersion"
$androidJar = Join-Path $sdk "platforms\android-34\android.jar"
$java = Join-Path $jbr "bin\java.exe"
$javac = Join-Path $jbr "bin\javac.exe"
$jar = Join-Path $jbr "bin\jar.exe"
$keytool = Join-Path $jbr "bin\keytool.exe"
$aapt2 = Join-Path $bt "aapt2.exe"
$d8 = Join-Path $sdk "build-tools\36.1.0\d8.bat"
$zipalign = Join-Path $sdk "build-tools\36.1.0\zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"

foreach ($tool in @($java, $javac, $jar, $keytool, $aapt2, $d8, $zipalign, $apksigner, $androidJar)) {
    if (-not (Test-Path $tool)) { throw "缺少构建工具: $tool" }
}

$env:JAVA_HOME = $jbr
$env:PATH = "$jbr\bin;$env:PATH"

$versionCode = 2
$versionName = "1.1.0"
$appId = "com.dsh.pianji"
$build = Join-Path $root "build"
$dist = Join-Path $root "dist"
$keystoreDir = Join-Path $root "keystore"
$keystore = Join-Path $keystoreDir "pianji-release.jks"
$storePass = "pianji-local-2026"
$keyAlias = "pianji"

foreach ($dir in @($build, $dist, $keystoreDir)) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
}
if (Test-Path (Join-Path $build "res.zip")) { Remove-Item (Join-Path $build "res.zip") -Force }
if (Test-Path (Join-Path $build "unsigned.apk")) { Remove-Item (Join-Path $build "unsigned.apk") -Force }
if (Test-Path (Join-Path $build "classes")) { Remove-Item (Join-Path $build "classes") -Recurse -Force }
if (Test-Path (Join-Path $build "dex")) { Remove-Item (Join-Path $build "dex") -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $build "classes") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $build "dex") -Force | Out-Null

Write-Host "[1/7] 编译资源..." -ForegroundColor Cyan
$aaptOutput = & $aapt2 compile --dir (Join-Path $root "app\src\main\res") -o (Join-Path $build "res.zip") 2>&1
if ($LASTEXITCODE -ne 0) { $aaptOutput | Write-Host; throw "aapt2 compile 失败" }

Write-Host "[2/7] 链接资源与清单..." -ForegroundColor Cyan
$linkArgs = @(
    "link",
    "-o", (Join-Path $build "unsigned.apk"),
    "-I", $androidJar,
    "--manifest", (Join-Path $root "app\src\main\AndroidManifest.xml"),
    "--java", (Join-Path $build "gen"),
    "--min-sdk-version", "24",
    "--target-sdk-version", "34",
    "--version-code", "$versionCode",
    "--version-name", "$versionName",
    "--auto-add-overlay",
    (Join-Path $build "res.zip")
)
$linkOutput = & $aapt2 @linkArgs 2>&1
if ($LASTEXITCODE -ne 0) { $linkOutput | Write-Host; throw "aapt2 link 失败" }

Write-Host "[3/7] 编译 Java..." -ForegroundColor Cyan
$javaFiles = Get-ChildItem (Join-Path $root "app\src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$javacArgs = @("-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
    "-classpath", $androidJar, "-d", (Join-Path $build "classes")) + $javaFiles
$javacOutput = & $javac @javacArgs 2>&1
if ($LASTEXITCODE -ne 0) { $javacOutput | Write-Host; throw "javac 编译失败" }
if ($javacOutput) { $javacOutput | Write-Host }

Write-Host "[4/7] 生成 DEX..." -ForegroundColor Cyan
$classFiles = Get-ChildItem (Join-Path $build "classes") -Recurse -Filter *.class | ForEach-Object { $_.FullName }
if ($classFiles.Count -eq 0) { throw "没有生成 class 文件" }
$d8Args = @("--release", "--min-api", "24", "--lib", $androidJar,
    "--output", (Join-Path $build "dex")) + $classFiles
$d8Output = & $d8 @d8Args 2>&1
if ($LASTEXITCODE -ne 0) { $d8Output | Write-Host; throw "d8 失败" }
if ($d8Output) { $d8Output | Write-Host }

Write-Host "[5/7] 打包 APK..." -ForegroundColor Cyan
$jarOutput = & $jar uf (Join-Path $build "unsigned.apk") -C (Join-Path $build "dex") classes.dex 2>&1
if ($LASTEXITCODE -ne 0) { $jarOutput | Write-Host; throw "写入 classes.dex 失败" }
$aligned = Join-Path $build "aligned.apk"
if (Test-Path $aligned) { Remove-Item $aligned -Force }
$alignOutput = & $zipalign -f -P 16 4 (Join-Path $build "unsigned.apk") $aligned 2>&1
if ($LASTEXITCODE -ne 0) { $alignOutput | Write-Host; throw "zipalign 失败" }

Write-Host "[6/7] 签名 APK..." -ForegroundColor Cyan
if (-not (Test-Path $keystore)) {
    Write-Host "生成新的本地签名密钥..." -ForegroundColor Yellow
    $keyOutput = & $keytool -genkeypair -v -keystore $keystore -alias $keyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $storePass -keypass $storePass `
        -dname "CN=Pianji, OU=Personal, O=Local First, L=Local, ST=Local, C=CN" -noprompt 2>&1
    if ($LASTEXITCODE -ne 0) { $keyOutput | Write-Host; throw "keytool 失败" }
    $keyOutput | Write-Host
}
$finalApk = Join-Path $dist "Pianji-$versionName.apk"
if (Test-Path $finalApk) { Remove-Item $finalApk -Force }
$signOutput = & $apksigner sign --ks $keystore --ks-key-alias $keyAlias `
    --ks-pass "pass:$storePass" --key-pass "pass:$storePass" `
    --out $finalApk $aligned 2>&1
if ($LASTEXITCODE -ne 0) { $signOutput | Write-Host; throw "apksigner 签名失败" }

Write-Host "[7/7] 校验 APK..." -ForegroundColor Cyan
$verifyOutput = & $apksigner verify --verbose --print-certs $finalApk 2>&1
if ($LASTEXITCODE -ne 0) { $verifyOutput | Write-Host; throw "APK 签名校验失败" }
$verifyOutput | Write-Host
$alignVerify = & $zipalign -c -P 16 -v 4 $finalApk 2>&1
if ($LASTEXITCODE -ne 0) { $alignVerify | Write-Host; throw "APK 对齐校验失败" }
& $aapt2 dump badging $finalApk 2>&1 | Select-Object -First 12 | Write-Host

$size = [math]::Round((Get-Item $finalApk).Length / 1KB, 1)
$sha = (Get-FileHash $finalApk -Algorithm SHA256).Hash
Write-Host ""
Write-Host "构建完成: $finalApk" -ForegroundColor Green
Write-Host "大小: $size KB" -ForegroundColor Green
Write-Host "SHA256: $sha" -ForegroundColor Green
