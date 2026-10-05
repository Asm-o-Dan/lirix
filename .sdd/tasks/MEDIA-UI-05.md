# Задача MEDIA-UI-05: Сворачиваемый винил и кинетический тонарм (Tonearm)

**Файл:** `app/src/main/java/com/eventengine/app/ui/components/AnalogTurntable.kt` (создать) и `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (изменить)
**Спека:** `.sdd/specs/media-ui/analog_turntable.md` (v1)

## 1. Сигнатура компонента:
```kotlin
package com.lirix.app.ui.components

@Composable
fun AnalogTurntable(
    isPlaying: Boolean,
    albumArtBitmap: ImageBitmap?,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
)
```

## 2. Поведение:
1. Отрисовывает вращающийся виниловый диск (диаметр 184dp) с концентрическими канавками и центрированной обложкой альбома.
2. Отрисовывает шарнирный тонарм звукоснимателя в правом верхнем секторе:
   - При `isPlaying == true`: плавный поворот на `0f` (игла касается пластинки) с физической пружиной `Spring.DampingRatioMediumBouncy`.
   - При `isPlaying == false`: плавный отвод на `-28f` (игла приподнята на парковочную стойку).
   - На кончике головки звукоснимателя — неоновый индикатор контакта иглы.
3. Поддерживает кнопку-сворачивание `[ ▴ Свернуть винил ]` / `[ ▾ Развернуть ]` с плавной вертикальной анимацией `AnimatedVisibility`.
4. В свёрнутом состоянии на экране отображается компактный мини-диск (32dp), при тапе на который винил разворачивается.

## 3. Критерий приёмки:
- Зеленые юнит-тесты `testDebugUnitTest`.
- Успешный `assembleDebug`.
- Проверка на POCO M7 (`2440cbe2`).
