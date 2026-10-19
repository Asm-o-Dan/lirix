package com.lirix.app.translations

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TranslationBlock(val original: String, val translated: String, val section: Boolean = false)

@Serializable
data class SongTranslation(
    val sourceUrl: String, val title: String, val artist: String, val translatedTitle: String,
    val translator: String, val blocks: List<TranslationBlock>, val language: String = "ru",
    val sourceName: String = "Amalgama", val aligned: Boolean = true,
    val warnings: List<String> = emptyList()
)

@Entity(tableName = "song_translations", primaryKeys = ["trackKey", "language", "sourceUrl"])
data class SongTranslationEntity(
    val trackKey: String, val language: String, val sourceUrl: String,
    val documentJson: String, val selectedAt: Long
)

@Dao
interface SongTranslationDao {
    @Query("SELECT * FROM song_translations WHERE trackKey = :trackKey AND language = :language ORDER BY selectedAt DESC, sourceUrl ASC")
    fun observe(trackKey: String, language: String = "ru"): Flow<List<SongTranslationEntity>>
    @Query("SELECT * FROM song_translations WHERE trackKey = :trackKey AND language = :language ORDER BY selectedAt DESC, sourceUrl ASC")
    suspend fun get(trackKey: String, language: String = "ru"): List<SongTranslationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SongTranslationEntity)
    @Transaction
    suspend fun saveAutomaticIfEmpty(entity: SongTranslationEntity): Boolean {
        if (get(entity.trackKey, entity.language).isNotEmpty()) return false
        save(entity)
        return true
    }
}

object TranslationDocuments {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(document: SongTranslation): String {
        validate(document)
        return json.encodeToString(document)
    }
    fun decode(text: String): SongTranslation {
        require(text.length <= 2_000_000) { "Слишком большой перевод" }
        return json.decodeFromString<SongTranslation>(text).also(::validate)
    }
    private fun validate(document: SongTranslation) {
        require(document.language in setOf("ru", "en", "es", "pt", "zh") && document.title.isNotBlank()) { "Некорректные данные перевода" }
        require(document.blocks.size in 1..3000 && document.blocks.any { it.translated.isNotBlank() }) { "В документе нет перевода" }
        require(document.blocks.sumOf { it.original.length + it.translated.length } <= 500_000) { "Слишком большой перевод" }
    }
}

const val CREATE_SONG_TRANSLATIONS = "CREATE TABLE IF NOT EXISTS `song_translations` (`trackKey` TEXT NOT NULL, `language` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `documentJson` TEXT NOT NULL, `selectedAt` INTEGER NOT NULL, PRIMARY KEY(`trackKey`, `language`, `sourceUrl`))"
