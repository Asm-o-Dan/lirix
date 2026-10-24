package com.lirix.app.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Cross-platform string localization catalog for Lirix (SPEC-I18N-01 / TASK-I18N-005).
 * Supports English (default) and Russian, shared between Android and Desktop Compose clients.
 */
enum class InterfaceLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String): InterfaceLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

interface StringsContract {
    val appName: String
    val noTrackPlaying: String
    val noTrackHint: String
    val untitledTrack: String
    val modeText: String
    val modeKaraoke: String
    val modeChords: String
    val modeStats: String
    val modeNotes: String
    val coverArtDesc: String
    val coverArtMissing: String
    val compactMode: String
    val connectDevices: String
    val selectTheme: String
    val loadingLyrics: String
    val lyricsNotFound: String
    val lyricsSynchronized: String
    val lyricsPlain: String
    val searchChords: String
    val historyTitle: String
    val libraryTitle: String
    val aboutTrack: String
    val blacklistTitle: String
    val wrappedTitle: String
    val romanizationOriginal: String
    val romanizationReadingAbove: String
    val romanizationRomanizedOnly: String
    val sharePoster: String
    val timingCalibration: String
}

object EnStrings : StringsContract {
    override val appName = "Lirix"
    override val noTrackPlaying = "Play music in your media player"
    override val noTrackHint = "Lirix will display lyrics and chords for currently playing song"
    override val untitledTrack = "Untitled"
    override val modeText = "Lyrics"
    override val modeKaraoke = "Teleprompter"
    override val modeChords = "Chords"
    override val modeStats = "Analytics"
    override val modeNotes = "Notes"
    override val coverArtDesc = "Track album artwork"
    override val coverArtMissing = "No album artwork"
    override val compactMode = "Compact mode"
    override val connectDevices = "Lirix Connect"
    override val selectTheme = "Select Theme"
    override val loadingLyrics = "Searching for lyrics and chords..."
    override val lyricsNotFound = "No lyrics found for this track"
    override val lyricsSynchronized = "Synced Karaoke"
    override val lyricsPlain = "Plain Lyrics"
    override val searchChords = "Search Chords"
    override val historyTitle = "Listening History"
    override val libraryTitle = "Music Library"
    override val aboutTrack = "About track"
    override val blacklistTitle = "Ignore Rules"
    override val wrappedTitle = "Listening Summary"
    override val romanizationOriginal = "Original"
    override val romanizationReadingAbove = "Reading above (Ruby)"
    override val romanizationRomanizedOnly = "Romanized only"
    override val sharePoster = "Share Poster"
    override val timingCalibration = "Timing Calibration"
}

object RuStrings : StringsContract {
    override val appName = "Lirix"
    override val noTrackPlaying = "Включите музыку в плеере ПК"
    override val noTrackHint = "Lirix покажет текст и аккорды текущей песни"
    override val untitledTrack = "Без названия"
    override val modeText = "Текст"
    override val modeKaraoke = "Суфлёр"
    override val modeChords = "Аккорды"
    override val modeStats = "Статистика"
    override val modeNotes = "Заметки"
    override val coverArtDesc = "Обложка трека"
    override val coverArtMissing = "Обложка отсутствует"
    override val compactMode = "Компактный режим"
    override val connectDevices = "Lirix Connect"
    override val selectTheme = "Выбрать тему"
    override val loadingLyrics = "Поиск текстов и аккордов..."
    override val lyricsNotFound = "Текст для этого трека не найден"
    override val lyricsSynchronized = "Синхронизированный суфлёр"
    override val lyricsPlain = "Текст песни"
    override val searchChords = "Поиск аккордов"
    override val historyTitle = "История прослушиваний"
    override val libraryTitle = "Медиатека"
    override val aboutTrack = "О треке"
    override val blacklistTitle = "Правила фильтрации"
    override val wrappedTitle = "Итоги прослушиваний"
    override val romanizationOriginal = "Оригинал"
    override val romanizationReadingAbove = "Чтение сверху (Ruby)"
    override val romanizationRomanizedOnly = "Только ромадзи/пиньинь"
    override val sharePoster = "Поделиться постером"
    override val timingCalibration = "Калибровка тайминга"
}

object LirixStrings {
    fun message(source: String, vararg arguments: Any?): String = UiMessageCatalog.text(source, currentLanguage, *arguments)
    private var _currentLanguage by mutableStateOf(InterfaceLanguage.EN)

    var currentLanguage: InterfaceLanguage
        get() = _currentLanguage
        set(value) {
            _currentLanguage = value
        }

    val current: StringsContract
        get() = when (_currentLanguage) {
            InterfaceLanguage.EN -> EnStrings
            InterfaceLanguage.RU -> RuStrings
        }
}
