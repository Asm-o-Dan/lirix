<#
.SYNOPSIS
    Fast Notification & Event Emulation via ADB for Android Event Engine.

.DESCRIPTION
    Sends realistic mock notifications directly to an Android device/emulator
    using 'adb shell cmd notification post' without requiring manual app interaction.

.PARAMETER Mode
    Emulation mode: 'hard_cases', 'category', 'all', 'single'. Default: 'hard_cases'.

.PARAMETER Category
    Target category when Mode='category' (e.g., 'finance.income', 'study.deadline').

.PARAMETER DelayMs
    Delay in milliseconds between consecutive notifications. Default: 800.

.PARAMETER Title
    Custom title when Mode='single'.

.PARAMETER Text
    Custom text when Mode='single'.

.PARAMETER AdbPath
    Custom path to adb.exe if not in PATH.

.EXAMPLE
    .\emulate_notifications.ps1 -Mode hard_cases
    .\emulate_notifications.ps1 -Mode category -Category study.deadline
    .\emulate_notifications.ps1 -Mode single -Title "Сбер" -Text "Перевод 1000"
#>

[CmdletBinding()]
param(
    [ValidateSet("hard_cases", "category", "all", "single")]
    [string]$Mode = "hard_cases",

    [string]$Category = "finance.income",

    [int]$DelayMs = 800,

    [string]$Title = "СберБанк",

    [string]$Text = "Перевод 1000",

    [string]$AdbPath = "adb"
)

# Set UTF-8 encoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Resolve-Adb {
    param([string]$customPath)

    if (Get-Command $customPath -ErrorAction SilentlyContinue) {
        return $customPath
    }

    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "C:\Android\platform-tools\adb.exe",
        "D:\Android\platform-tools\adb.exe"
    )

    foreach ($p in $commonPaths) {
        if (Test-Path $p) {
            Write-Host "[ADB] Found ADB at: $p" -ForegroundColor Green
            return $p
        }
    }

    Write-Error "ADB executable not found! Ensure Android SDK platform-tools is installed or specify -AdbPath."
    exit 1
}

$AdbCmd = Resolve-Adb $AdbPath

# Verify connected device
$devices = & $AdbCmd devices
Write-Host "Connected devices:" -ForegroundColor Cyan
Write-Host $devices

$hasDevice = $devices | Where-Object { $_ -match "\tdevice$" }
if (-not $hasDevice) {
    Write-Warning "No active Android device or emulator detected. Please connect a device with USB Debugging enabled."
}

function Send-AdbNotification {
    param(
        [string]$NotificationTag,
        [string]$NotificationTitle,
        [string]$NotificationText
    )

    # Clean special quotes
    $cleanTitle = $NotificationTitle -replace '"', '\"'
    $cleanText = $NotificationText -replace '"', '\"'

    Write-Host ">>> Posting Notification: [$cleanTitle] $cleanText" -ForegroundColor Yellow

    # Android command syntax: cmd notification post -S bigtext -t <tag> <tag> <text>
    # Note: Notification title is passed as tag or inside BigText style
    & $AdbCmd shell cmd notification post -S bigtext -t "$NotificationTag" "$cleanTitle" "$cleanText"
}

# Dataset file path
$datasetFile = Join-Path $PSScriptRoot "..\dataset\golden_dataset.json"

if ($Mode -eq "single") {
    Send-AdbNotification -NotificationTag "manual_test" -NotificationTitle $Title -NotificationText $Text
    exit 0
}

if (-not (Test-Path $datasetFile)) {
    Write-Error "Golden dataset not found at $datasetFile!"
    exit 1
}

$rawJson = Get-Content -Path $datasetFile -Raw -Encoding UTF8
$dataset = $rawJson | ConvertFrom-Json
$samples = $dataset.samples

Write-Host "`n=== Android Event Engine - Notification Emulation ===" -ForegroundColor Cyan
Write-Host "Dataset version: $($dataset.version)"
Write-Host "Mode: $Mode"

$targetSamples = @()

switch ($Mode) {
    "hard_cases" {
        $targetSamples = $samples | Where-Object { $_.is_hard_case -eq $true }
        Write-Host "Selected $($targetSamples.Count) hard-cases for emulation." -ForegroundColor Green
    }
    "category" {
        $targetSamples = $samples | Where-Object { $_.category -eq $Category }
        Write-Host "Selected $($targetSamples.Count) samples for category '$Category'." -ForegroundColor Green
    }
    "all" {
        $targetSamples = $samples
        Write-Host "Selected all $($targetSamples.Count) samples for emulation." -ForegroundColor Green
    }
}

$count = 0
foreach ($sample in $targetSamples) {
    $count++
    $tag = "aee_test_$($sample.id)"
    $title = if ($sample.title) { $sample.title } else { "System" }
    $text = $sample.text

    Write-Host "[$count/$($targetSamples.Count)] ID: $($sample.id) | Category: $($sample.category) | HardCase: $($sample.is_hard_case)" -ForegroundColor DarkGray
    Send-AdbNotification -NotificationTag $tag -NotificationTitle $title -NotificationText $text

    if ($count -lt $targetSamples.Count -and $DelayMs -gt 0) {
        Start-Sleep -Milliseconds $DelayMs
    }
}

Write-Host "`n[DONE] Successfully emulated $count notifications via ADB." -ForegroundColor Green
