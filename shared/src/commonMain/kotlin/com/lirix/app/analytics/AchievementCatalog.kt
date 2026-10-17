package com.lirix.app.analytics

import com.lirix.app.storage.AchievementEntity

/** Stable achievement definitions shared with the Android 1.4 catalog. */
object AchievementCatalog {
    fun defaults(): List<AchievementEntity> = listOf(
        AchievementEntity("FIRST_TRACK", "Первая нота", "Прослушан 1 трек", "ic_music_note", maxProgress = 1, tier = "BRONZE", category = "LISTENING"),
        AchievementEntity("DISCOVERY_5", "Первые шаги", "Послушано 5 разных артистов", "ic_explore", maxProgress = 5, tier = "BRONZE", category = "EXPLORATION"),
        AchievementEntity("LYRICS_NOVICE", "Подпевала", "Открыт текст песни", "ic_lyrics", maxProgress = 1, tier = "BRONZE", category = "LYRICS"),
        AchievementEntity("CHORD_STRUMMER", "Первый аккорд", "Открыты аккорды к треку", "ic_music_note", maxProgress = 1, tier = "BRONZE", category = "LYRICS"),
        AchievementEntity("SHORT_SESSION", "Разминка", "15 минут музыки за сессию", "ic_timer", maxProgress = 1, tier = "BRONZE", category = "LISTENING"),
        AchievementEntity("NOTE_TAKER", "Заметки на полях", "Добавлена первая личная заметка к треку", "ic_edit", maxProgress = 1, tier = "BRONZE", category = "SPECIAL"),
        AchievementEntity("STREAK_3", "На волне", "Слушайте музыку 3 дня подряд", "ic_repeat", maxProgress = 3, tier = "BRONZE", category = "LISTENING"),
        AchievementEntity("CENTURY_CLUB", "Клуб сотни", "100 уникальных треков в библиотеке", "ic_disc", maxProgress = 100, tier = "SILVER", category = "EXPLORATION"),
        AchievementEntity("ARTIST_DEVOTEE", "Преданный фанат", "Один исполнитель прослушан 15 раз", "ic_star", maxProgress = 15, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("KARAOKE_REGULAR", "Караоке-бар", "10 треков с караоке LRC", "ic_lyrics", maxProgress = 10, tier = "SILVER", category = "LYRICS"),
        AchievementEntity("STREAK_7", "Музыкальная неделя", "Слушайте музыку 7 дней подряд", "ic_repeat", maxProgress = 7, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("MARATHON_LISTENER", "Марафонец", "Сессия прослушивания более 2 часов", "ic_timer", maxProgress = 1, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("MORNING_ENERGY", "Бодрое утро", "5 сессий с 06:00 до 10:00 утра", "ic_sun", maxProgress = 5, tier = "SILVER", category = "SPECIAL"),
        AchievementEntity("EVENING_CHILL", "Вечерний релакс", "10 сессий вечером (18:00 - 23:00)", "ic_moon", maxProgress = 10, tier = "SILVER", category = "SPECIAL"),
        AchievementEntity("GENRE_EXPLORER", "Меломан", "Более 15 разных исполнителей", "ic_explore", maxProgress = 15, tier = "SILVER", category = "EXPLORATION"),
        AchievementEntity("LIBRARY_300", "Золотая фонотека", "300 уникальных треков в библиотеке", "ic_disc", maxProgress = 300, tier = "GOLD", category = "EXPLORATION"),
        AchievementEntity("REPEAT_FANATIC", "На повторе", "Один трек прослушан более 30 раз", "ic_repeat", maxProgress = 30, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("KARAOKE_MASTER", "Звезда караоке", "25 треков с LRC-караоке", "ic_lyrics", maxProgress = 25, tier = "GOLD", category = "LYRICS"),
        AchievementEntity("NIGHT_OWL", "Ночная сова", "10 сессий глубокой ночью (01:00 - 05:00)", "ic_moon", maxProgress = 10, tier = "GOLD", category = "SPECIAL"),
        AchievementEntity("STREAK_30", "Железная привычка", "30 дней непрерывного прослушивания", "ic_repeat", maxProgress = 30, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("MARATHON_5H", "Аудио-марафон 5ч", "Более 5 часов музыки за один день", "ic_timer", maxProgress = 1, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("ALBUM_COLLECTOR", "Хранитель винила", "20 треков с обложками альбомов", "ic_disc", maxProgress = 20, tier = "GOLD", category = "EXPLORATION"),
        AchievementEntity("SECRET_MIDNIGHT", "Полуночная тайна", "Включен трек в первые 5 минут полуночи (00:00 - 00:05)", "ic_star", maxProgress = 1, tier = "PLATINUM", category = "SPECIAL"),
        AchievementEntity("SECRET_REPEAT_DAY", "Одержимость дня", "Один трек прослушан 15 раз за одни сутки", "ic_repeat", maxProgress = 1, tier = "PLATINUM", category = "LISTENING"),
        AchievementEntity("DISCOGRAPHY_BINGE", "Дискографический запой", "8 разных треков одного артиста за сутки", "ic_explore", maxProgress = 8, tier = "PLATINUM", category = "EXPLORATION"),
        AchievementEntity("PLATINUM_PERFECTION", "Абсолютный слух", "Разблокировано 20 любых достижений", "ic_star", maxProgress = 20, tier = "PLATINUM", category = "SPECIAL")
    )
}
