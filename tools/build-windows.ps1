<#
.SYNOPSIS
    Build the Android debug APK on a Windows PC without GitHub Actions.
.DESCRIPTION
    One-time downloads: portable JDK 17 (if Java is missing), Gradle 8.11.1,
    official Android command-line tools, SDK Platform 36 and Build Tools 35.
    The script prompts for Android SDK licence acceptance.
    It then compiles/tests the CURRENT checkout and produces dist/Timelapse-debug.apk.
    The build never uploads source or signing keys to any third-party CI provider.

    Usage:
        powershell -ExecutionPolicy Bypass -File tools/build-windows.ps1
        powershell -ExecutionPolicy Bypass -File tools/build-windows.ps1 -SkipTests

    Run in the repository working directory. Requires Internet on first run.
    This script has not yet been executed on a Windows machine.
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$SkipDownloads,
    [switch]$InstallToDevice
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cache = Join-Path $env:LOCALAPPDATA 'TimelapseBuild'
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$gradleVersion = '8.13'
$gradleBin = Join-Path $cache "gradle-$gradleVersion\bin\gradle.bat"
$cmdlineTools = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$androidToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
$androidToolsSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

function Log([string]$message) {
    Write-Host "[Timelapse] $message" -ForegroundColor Cyan
}
function Download([string]$url,[string]$destination) {
    if ($SkipDownloads) {
        throw "Missing dependency at $destination, but -SkipDownloads was set"
    }
    Log "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $destination -UseBasicParsing -TimeoutSec 600
}
function Require-ExitCode([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed (exit code $LASTEXITCODE). No APK will be labelled successful."
    }
}
function Ensure-Java {
    $existing = Get-Command java -ErrorAction SilentlyContinue
    if ($existing) {
        Log "Using existing Java: $($existing.Source)"
        return
    }
    $androidStudioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $androidStudioJbr 'bin\java.exe')) {
        $env:JAVA_HOME = $androidStudioJbr
    } else {
        $jdkHome = Join-Path $cache 'jdk-17'
        if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
            New-Item -ItemType Directory -Force -Path $jdkHome | Out-Null
            $zip = Join-Path $cache 'temurin-17.zip'
            # Adoptium official API: current latest GA JDK 17 x64 Windows portable archive.
            Download 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $zip
            $stage = Join-Path $cache 'temp-jdk'
            if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
            Expand-Archive -Path $zip -DestinationPath $stage
            $javaCandidate = Get-ChildItem -Path $stage -Filter java.exe -Recurse |
                Where-Object { $_.FullName -match '\\bin\\java\.exe$' } | Select-Object -First 1
            if (-not $javaCandidate) { throw 'Temurin JDK download did not contain java.exe' }
            $sourceHome = Split-Path (Split-Path $javaCandidate.FullName -Parent) -Parent
            Copy-Item -Path (Join-Path $sourceHome '*') -Destination $jdkHome -Recurse -Force
            Remove-Item -Recurse -Force $stage
        }
        $env:JAVA_HOME = $jdkHome
    }
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    Log "Java available at $env:JAVA_HOME"
}
function Ensure-Gradle {
    if (Test-Path $gradleBin) { return }
    $zip = Join-Path $cache "gradle-$gradleVersion-bin.zip"
    Download $gradleUrl $zip
    Expand-Archive -Path $zip -DestinationPath $cache -Force
    if (-not (Test-Path $gradleBin)) { throw 'Gradle distribution extraction failed' }
}
function Ensure-AndroidSdk {
    if (-not (Test-Path $cmdlineTools)) {
        $zip = Join-Path $cache 'android-commandline-tools.zip'
        Download $androidToolsUrl $zip
        $got = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
        if ($got -ne $androidToolsSha256) {
            Remove-Item -Force $zip
            throw 'Android command-line tools checksum mismatch. Refusing installation.'
        }
        $stage = Join-Path $cache 'temp-cmdline'
        if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
        Expand-Archive -Path $zip -DestinationPath $stage -Force
        if (-not (Test-Path (Join-Path $stage 'cmdline-tools\bin\sdkmanager.bat'))) {
            throw 'Unexpected Android command-line tools archive layout'
        }
        $latest = Join-Path $sdk 'cmdline-tools\latest'
        New-Item -ItemType Directory -Force -Path (Split-Path $latest) | Out-Null
        Move-Item (Join-Path $stage 'cmdline-tools') $latest
        Remove-Item -Recurse -Force $stage
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-36\android.jar')) -or
        -not (Test-Path (Join-Path $sdk 'build-tools\35.0.0\aapt2.exe'))) {
        Log 'Android licences must be accepted in the following prompt.'
        & $cmdlineTools --sdk_root="$sdk" --licenses
        Require-ExitCode 'Android SDK licence acceptance'
        & $cmdlineTools --sdk_root="$sdk" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
        Require-ExitCode 'Android SDK installation'
    }
}

New-Item -ItemType Directory -Force -Path $cache | Out-Null
Push-Location $repo
try {
    Ensure-Java
    Ensure-Gradle
    Ensure-AndroidSdk
    $env:GRADLE_USER_HOME = Join-Path $cache 'gradle-cache'
    Log "Building repository revision: $repo"
    $tasks = if ($SkipTests) { @(':app:assembleDebug') } else { @(':app:testDebugUnitTest', ':app:assembleDebug') }
    & $gradleBin --no-daemon @tasks
    Require-ExitCode 'Gradle Android build'
    $source = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $source)) { throw "Gradle completed but APK not found at $source" }
    $destinationDir = Join-Path $repo 'dist'
    New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null
    $destination = Join-Path $destinationDir 'Timelapse-debug.apk'
    Copy-Item -Force $source $destination
    $hash = (Get-FileHash -Algorithm SHA256 $destination).Hash
    Log "APK BUILT: $destination"
    Log "SHA256: $hash"
    Log 'DEBUG build only: application camera functionality is not yet hardware/soak tested.'
    if ($InstallToDevice) {
        $adb = Join-Path $sdk 'platform-tools\adb.exe'
        if (-not (Test-Path $adb)) { throw 'adb not found' }
        & $adb devices
        & $adb install -r $destination
        Require-ExitCode 'ADB install'
    }
}
finally { Pop-Location }
