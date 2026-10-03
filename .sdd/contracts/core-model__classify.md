# Межзонный контракт: Core Model ↔ Classify Rules (Семантическая классификация и прототипы)

**Версия:** FROZEN v2  
**Дата заморозки:** 2026-09-27  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер: `zone/core-model` (`:core:model`)
- Потребители: `zone/classify-rules` (`:classify:rules`), `zone/core-storage` (`:core:storage`), `zone/ui-timeline` (`:ui:timeline`), `zone/app-lifecycle` (`:app`)

---

### 1. Доменные перечисления и модели данных

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Event
import java.time.Instant

/**
 * Доменные категории классификации событий и уведомлений.
 */
enum class Category {
    FINANCE,        // Банковские операции, SMS-банкинг, чеки, баланс, переводы
    COMMUNICATION,  // Личные и групповые мессенджеры, SMS-переписка, почта, звонки
    MUSIC,          // Мультимедиа, воспроизведение аудио/видео треков
    SERVICES,       // Сервисные уведомления, доставка, такси, системные статусы, утилиты
    ADVERTISEMENT,  // Реклама, промо-рассылки, маркетинговые акции (спам)
    OTHER,          // Прочие события, не подпадающие под основные категории
    UNCLASSIFIED;   // Начальное неклассифицированное состояние события

    companion object {
        fun fromStringOrUnclassified(raw: String?): Category {
            if (raw.isNullOrBlank()) return UNCLASSIFIED
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: UNCLASSIFIED
        }

        // Псевдоним для совместимости со спеками
        val UNKNOWN: Category get() = UNCLASSIFIED
    }
}

/**
 * Механизм / движок, назначивший категорию событию.
 */
enum class Engine {
    NONE,       // Категория не назначена (по умолчанию)
    PROTOTYPE,  // Назначено по базе подтвержденных пользовательских прототипов (supportCount >= 2)
    RULES,      // Назначено статическим детерминированным правилом (эвристика)
    USER;       // Назначено прямой ручной правкой пользователя в UI (Feedback Loop)

    companion object {
        fun fromStringOrDefault(raw: String?, default: Engine = NONE): Engine {
            if (raw.isNullOrBlank()) return default
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: default
        }
    }
}

/**
 * Строго валидированный уровень уверенности классификации в диапазоне [0.0 .. 1.0].
 */
@JvmInline
value class Confidence(val value: Double) : Comparable<Confidence> {
    init {
        require(value in 0.0..1.0) { "Confidence value must be in range [0.0, 1.0], but was: $value" }
    }

    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Confidence(0.0)
        val MAXIMUM = Confidence(1.0)
        val HIGH_THRESHOLD = Confidence(0.85)
        val PROTOTYPE_CONFIDENCE = Confidence(1.0)
    }
}

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

/**
 * Результат выполнения семантической классификации события.
 *
 * @property category Итоговая доменная категория.
 * @property confidence Степень уверенности (от 0.0 до 1.0).
 * @property engine Механизм, определивший категорию.
 * @property contentFingerprint SHA-256 хэш шаблона текста события.
 */
data class ClassificationResult(
    val category: Category,
    val confidence: Double,
    val engine: Engine,
    val contentFingerprint: String
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in 0.0..1.0 (got $confidence)" }
        require(contentFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "contentFingerprint must be 64-char hex SHA-256 (got '$contentFingerprint')"
        }
    }

    val typedConfidence: Confidence get() = Confidence(confidence)

    companion object {
        fun unclassified(fingerprint: String): ClassificationResult =
            ClassificationResult(
                category = Category.UNCLASSIFIED,
                confidence = 0.0,
                engine = Engine.NONE,
                contentFingerprint = fingerprint
            )

        fun fromPrototype(prototype: UserPrototype): ClassificationResult =
            ClassificationResult(
                category = prototype.category,
                confidence = 1.0,
                engine = Engine.PROTOTYPE,
                contentFingerprint = prototype.fingerprint
            )
    }
}
```

---

### 2. Контракт семантического классификатора `SemanticClassifier`

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Event

/**
 * Контракт детерминированного семантического классификатора событий.
 *
 * Реализует двухфазную чистую классификацию:
 * 1. Prototype-First: при наличии в БД прототипа с supportCount >= 2 возвращает его категорию с confidence = 1.0.
 * 2. Rule-Based Heuristics: при отсутствии прототипа применяет статические правила на основе пакета и текста.
 */
interface SemanticClassifier {

    /**
     * Выполняет классификацию события с учетом ранее найденного пользовательского прототипа.
     *
     * @param event Структурированное событие.
     * @param prototype Опциональный пользовательский прототип для данного пакета и fingerprint (если найден в хранилище).
     * @return Детерминированный результат классификации ClassificationResult.
     */
    fun classify(event: Event, prototype: UserPrototype?): ClassificationResult
}
```

---

### 3. Контракт строгой пакетной маршрутизации `PackageGatedRouter`

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.SourceId

/**
 * Контракт строгой фильтрации и изоляции источников по пакетам и отправителям.
 * Исключает ложноположительные срабатывания (кейс InTour Telegram и Яндекс.Погода).
 */
interface PackageGatedRouter {

    /**
     * Проверяет, разрешено ли источнику претендовать на категорию FINANCE.
     *
     * @param packageName Имя Android-пакета (например: "com.apb.mobile", "com.radolyn.ayugram").
     * @param senderOrTitle Имя отправителя или заголовок (для SMS: "APB", "900", "InTour").
     * @param sourceId Подсистема сбора (NOTIFICATION, SMS, MEDIA).
     * @return true ТОЛЬКО если источник входит в доверенный финансовый белый список.
     */
    fun canClassifyAsFinance(packageName: String, senderOrTitle: String?, sourceId: SourceId): Boolean

    /**
     * Проверяет, входит ли пакет в жесткий черный список мессенджеров/соцсетей,
     * для которых категория FINANCE безусловно заблокирована.
     */
    fun isMessengerBlacklisted(packageName: String): Boolean

    companion object {
        /** Доверенные банковские приложения (пакеты) */
        val BANK_PACKAGES = setOf(
            "com.apb.mobile",          // Агропромбанк ПМР
            "com.prisbank.app",        // Приднестровский Сбербанк
            "md.maib.maibank"          // BC MAIB Молдова
        )

        /** Доверенные отправители для банковских SMS */
        val BANK_SMS_SENDERS = setOf(
            "APB", "AGROPROMBANK", "PRISBANK", "SBERBANK", "MAIB", "900"
        )

        /** Пакеты мессенджеров (жесткий запрет FINANCE) */
        val MESSENGER_PACKAGES = setOf(
            "com.radolyn.ayugram",
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.viber.voip",
            "org.signal.messenger",
            "com.facebook.orca",
            "com.vkontakte.android",
            "com.discord",
            "com.skype.raider",
            "com.slack"
        )
    }
}
```

---

### 4. Гарантии и правила интеграции (GATE 3)
1. **Чистый Kotlin JVM:** Никаких зависимостей от Android SDK и Room.
2. **Prototype Feedback Inversion Guard:** Проверка прототипов (`UserPrototype.supportCount >= 2`) выполняется строго **ДО** применения любых эвристических правил.
3. **Финансовая изоляция:** Категория `Category.FINANCE` назначается только при условии `canClassifyAsFinance(...) == true`.
