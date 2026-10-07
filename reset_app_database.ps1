# Скрипт полного сброса базы данных и чистой переустановки
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "=== Ожидание подключения устройства... ===" -ForegroundColor Cyan
& $adb wait-for-device

Write-Host "=== Очистка данных приложения com.eventengine.app.debug... ===" -ForegroundColor Yellow
& $adb shell pm clear com.eventengine.app.debug

Write-Host "=== Установка свежего билда приложения... ===" -ForegroundColor Green
& $adb install -r app/build/outputs/apk/debug/app-debug.apk

Write-Host "=== Запуск приложения с чистого листа... ===" -ForegroundColor Green
& $adb shell monkey -p com.eventengine.app.debug -c android.intent.category.LAUNCHER 1

Write-Host "=== ГОТОВО! База чиста, начинаем с нуля. ===" -ForegroundColor Green
