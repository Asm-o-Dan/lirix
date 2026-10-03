package com.example.npc.core.model.classify

import java.time.Instant

/**
 * Пользовательский прототип (шаблон обратной связи).
 * Сохраняется в БД при ручной коррекции категории пользователем.
 *
 * @property id Суррогатный первичный ключ (0L до сохранения в БД).
 * @property packageName Имя Android-пакета приложения-источника (например: "com.radolyn.ayugram").
 * @property fingerprint SHA-256 хэш нормализованного шаблона текста события.
 * @property category Назначенная пользователем категория.
 * @property supportCount Количество подтверждений пользователем (>= 1).
 * @property createdAt Время первой фиксации прототипа.
 * @property lastSeenAt Время последнего подтверждения пользователем.
 */
data class UserPrototype(
    val id: Long = 0L,
    val packageName: String,
    val fingerprint: String,
    val category: Category,
    val supportCount: Int,
    val createdAt: Instant,
    val lastSeenAt: Instant = createdAt
) {
    init {
        require(id >= 0L) { "UserPrototype id must be >= 0 (got $id)" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(fingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "fingerprint must be 64-char lowercase hex SHA-256 (got '$fingerprint')"
        }
        require(supportCount >= 1) { "supportCount must be >= 1 (got $supportCount)" }
        require(!createdAt.isAfter(lastSeenAt)) { "createdAt ($createdAt) cannot be after lastSeenAt ($lastSeenAt)" }
    }

    /** Признак достижения порога доверия для применения Prototype-First логики (supportCount >= 2). */
    val isConfident: Boolean get() = supportCount >= CONFIDENCE_THRESHOLD

    companion object {
        const val CONFIDENCE_THRESHOLD = 2
    }
}
