# Спецификация: zone/pipeline-spi

## Модули: `:pipeline:nodes-api`, `:pipeline:nodes-builtin` · Зона: `zone/pipeline-spi` · Фаза 2: «Конструктор конвейеров» · Статус: `PROPOSED`

---

### 1. Назначение, архитектурный контекст и границы модулей

Модули зоны `zone/pipeline-spi` образуют фундаментальный программный интерфейс поставщика услуг (Service Provider Interface, SPI) и стандартную библиотеку узлов конвейера обработки событий. Они физически разделены на два независимых Gradle-модуля:
1. **`:pipeline:nodes-api` (SPI Контракты):**
   - Абстрактное ядро спецификаций узлов (`NodeSpec`), исполнителей (`NodeExecutor`), регистровой памяти (`Frame`), буфера эффектов (`EffectBuffer`), системы типов и каталога (`NodeRegistry`).
   - Чистый Kotlin JVM модуль (`kotlin-jvm`).
   - **Строгий запрет зависимостей:** Zero Android (`android.*`, `androidx.*`), Zero Room, Zero UI, Zero DI frameworks.
2. **`:pipeline:nodes-builtin` (Стандартная библиотека узлов):**
   - Готовая реализация стандартного набора узлов фильтрации, классификации, санитайзинга и финансовой экстракции, реализующих логику эталонного конвейера Фазы 1.1.
   - Зависит только от `:pipeline:nodes-api`, `:core:model` и линейного движка регулярных выражений `com.google.re2j:re2j`.
   - Zero Android, Zero SQLite.

#### 1.1. Роль в архитектуре Фазы 2 (Двухфазная модель разделения ответственности)

В соответствии с центральным принципом Фазы 2 **«Компилируй один раз, исполняй многократно»**, SPI узла разделено на две фазы жизненного цикла:

```
                ┌────────────────────────────────────────────────────────┐
                │          ФАЗА КОМПИЛЯЦИИ (Compile-Time / Cold)         │
                │        NodeSpec (Схема, Типы, Валидация, Bind)         │
                │     Аллокации разрешены. Рефлексия/парсинг допустимы   │
                └───────────────────────────┬────────────────────────────┘
                                            │
                                  spec.bind(BindContext)
                                            │
                                            ▼
                ┌────────────────────────────────────────────────────────┐
                │        ФАЗА ИСПОЛНЕНИЯ (Runtime / Hot Path Loop)       │
                │      NodeExecutor.execute(Frame, EffectBuffer)         │
                │    СТРОГО ZERO ALLOCATION, ZERO REFLECTION, ZERO I/O   │
                │       Прямой доступ к примитивным массивам Frame       │
                └────────────────────────────────────────────────────────┘
```

| Фаза | Участник | Ответственность | Допустимые операции | Бюджет |
|---|---|---|---|---|
| **Compile-time** | `NodeSpec`, `ParamSchema`, `PortSchema` | Валидация параметров AST, проверка типов входов/выходов, расчет зависимостей, прекомпиляция паттернов, аллокация слотов | Парсинг строк, создание объектов, аллокации коллекций, валидация RE2/J | $\le 5$ мс на конвейер |
| **Runtime (Hot)** | `NodeExecutor`, `Frame`, `EffectBuffer` | Прямое вычисление над индексами регистров, запись промежуточных значений, постановка эффектов в буфер | Только побитовые операции, чтение/запись примитивных массивов, вызов скомпилированных матчеров без аллокаций | $\le 15$ мкс на узел, **0 байт** аллокаций |

#### 1.2. Фундаментальные архитектурные инварианты SPI

1. **Чистая JVM-модель (Zero Android OS):** Никаких импортов `android.content.Context`, `android.os.Bundle` или `android.service.notification.StatusBarNotification`. Входные данные передаются через плоские примитивные регистры и неизменяемые текстовые регистры `TextRegister`. Модули собираются и тестируются на стандартной JVM за доли секунды.
2. **Абсолютный Zero-Allocation контракт на горячем пути:** Метод `NodeExecutor.execute(frame: Frame, buffer: EffectBuffer): StepResult` **не выделяет ни одного байта в Java Heap** в установившемся режиме работы. Запрещены:
   - Создание любых объектов (`new`, инстанцирование `data class`).
   - Боксинг примитивов (применение Generics, `java.lang.Long`, nullable value classes).
   - Вызовы `String.format`, интерполяции строк `"$a$b"`, `substring()`, `split()`.
   - Итераторы коллекций (`for (x in list)`). Используются только классические индексные циклы `for (i in 0 until size)` по плоским массивам.
   - Поиск по строковым именам (`Map.get("varName")`). Все имена разрешаются в `Int`-индексы на этапе компиляции.
3. **Двухфазная изоляция эффектов (Two-Phase Commit):** Узлы **никогда не обращаются к внешнему миру напрямую** (нет прямых вызовов Room DAO, репозиториев, сетевых сокетов или системных служб). Все побочные действия кодируются и записываются в предвыделенный буфер `EffectBuffer`. Фактический коммит эффектов в базу данных производит хост-оркестратор **только после успешного завершения всего конвейера с терминальным сигналом `PASS`**.
4. **Отказоустойчивость и локализация сбоев (Failure Isolation):** Сбой одного узла (выброс исключения или таймаут) не роняет хост-процесс и не оставляет конвейер в неконсистентном состоянии. Буфер эффектов узла откатывается к контрольной точке (`mark`), в выходные регистры записываются безопасные дефолтные значения (`failureDefault`), а конвейер либо переходит по ветке ошибки, либо безопасно завершается.
5. **Защита от ReDoS и бесконечных циклов:** Использование бэктрекинговых регулярных выражений (`java.util.regex`) строго запрещено. Разрешен исключительно детерминированный движок **RE2/J** с линейным временем $O(n)$. Защита от бесконечных циклов обеспечивается аппаратным счетчиком `budget` в объекте `Frame`.

---

### 2. SPI Контракты (`:pipeline:nodes-api`)

Пакет: `com.example.npc.pipeline.nodes.api`

```
:pipeline:nodes-api
├── frame/
│   ├── Bank.kt                  # Перечисление регистровых банков (LONG, DOUBLE, REF, TEXT)
│   ├── FrameLayout.kt           # Спецификация емкости регистровой машины
│   ├── Frame.kt                 # Рабочий регистровый контекст потока с массивами
│   └── TextRegister.kt          # Мутабельный безаллокационный буфер символов CharSequence
├── effect/
│   ├── EffectBuffer.kt          # Предвыделенный плоский буфер отложенных эффектов
│   ├── EffectKindId.kt          # Строковый идентификатор типа эффекта
│   ├── EffectKindDef.kt         # Описание контракта аргументов эффекта и политики слияния
│   ├── MergePolicy.kt           # Политики слияния эффектов (LAST_WINS, FIRST_WINS, ACCUMULATE, ERROR)
│   └── EffectView.kt            # Итератор по буферу эффектов для фазы коммита
├── executor/
│   ├── StepResult.kt            # Inlined Long результат исполнения узла (OP_NEXT, OP_JUMP, OP_HALT, OP_FAIL)
│   ├── NodeExecutor.kt          # Функциональный интерфейс горячего исполнения
│   └── FailurePolicy.kt         # Политики обработки сбоев узла (ABORT, SKIP, ROUTE)
├── spec/
│   ├── NodeSpec.kt              # Главный SPI-контракт метаданных и компиляции узла
│   ├── NodeTypeId.kt            # Уникальный строковый идентификатор узла
│   ├── NodeCategory.kt          # Категория узла (CONDITION, TRANSFORM, ACTION)
│   ├── NodeTraits.kt            # Свойства узла (чистота, детерминизм, оценка стоимости)
│   ├── ParamSchema.kt           # Декларативная схема параметров узла
│   ├── PortSchema.kt            # Схема входных/выходных портов и ветвлений
│   ├── BindContext.kt           # Контекст связывания компилятора с регистровой машиной
│   └── ScratchSpec.kt           # Спецификация предвыделенного рабочего объекта (Matcher и др.)
└── registry/
    ├── NodeRegistry.kt          # Потокобезопасный каталог зарегистрированных спецификаций
    └── NodeModule.kt            # Модульный компоновщик для групповой регистрации узлов
```

#### 2.1. Регистровая модель данных: `Bank`, `TextRegister`, `FrameLayout`, `Frame`

Вместо динамических словарей `Map<String, Any>` рантайм использует регистровую виртуальную машину. Регистры сгруппированы в четыре плоских банка памяти (`Bank`):

```kotlin
package com.example.npc.pipeline.nodes.api.frame

/**
 * Регистровые банки памяти виртуальной машины конвейера.
 */
enum class Bank {
    /** 64-битные целые числа. Хранит Long, Int, Short, Byte, Boolean (0L/1L), Enums (ordinal), EpochMillis. */
    LONG,

    /** 64-битные вещественные числа IEEE 754 (Double, Float). */
    DOUBLE,

    /** Иммутабельные ссылки на тяжелые DTO хоста (FinancialTransaction, Category, UserPrototype) и scratch-объекты. */
    REF,

    /** Предвыделенные текстовые регистры с фиксированной емкостью, без аллокаций в Heap. */
    TEXT
}
```

##### 2.1.1. Безаллокационный текстовый регистр `TextRegister`
Для предотвращения аллокаций строк (`String.substring`, `toLowerCase` и др.) строковые трансформации производятся в предвыделенных текстовых ячейках, реализующих `java.lang.CharSequence`:

```kotlin
package com.example.npc.pipeline.nodes.api.frame

/**
 * Мутабельный строковый регистр фиксированной емкости.
 * Реализует [CharSequence], что позволяет передавать его напрямую в RE2/J Matcher.reset()
 * без аллокаций новых строковых объектов.
 */
class TextRegister(val capacity: Int = 1024) : CharSequence {
    @JvmField val chars: CharArray = CharArray(capacity)
    @JvmField var len: Int = 0
    @JvmField var isTruncated: Boolean = false

    override val length: Int get() = len

    override fun get(index: Int): Char {
        if (index < 0 || index >= len) throw IndexOutOfBoundsException("Index: $index, Length: $len")
        return chars[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        throw UnsupportedOperationException("subSequence allocates memory; use in-place char inspection")
    }

    fun set(source: CharSequence) {
        val srcLen = source.length
        val copyLen = if (srcLen > capacity) {
            isTruncated = true
            capacity
        } else {
            isTruncated = false
            srcLen
        }
        for (i in 0 until copyLen) {
            chars[i] = source[i]
        }
        len = copyLen
    }

    fun clear() {
        len = 0
        isTruncated = false
    }

    fun copyTo(destination: TextRegister) {
        val copyLen = minOf(this.len, destination.capacity)
        System.arraycopy(this.chars, 0, destination.chars, 0, copyLen)
        destination.len = copyLen
        destination.isTruncated = this.isTruncated || (this.len > destination.capacity)
    }

    /** Создает неизменяемый String ТОЛЬКО на фазе фиксации (Commit Phase). */
    fun materializeString(): String = String(chars, 0, len)
}
```

##### 2.1.2. Описание емкости `FrameLayout` и рабочий контекст `Frame`

```kotlin
package com.example.npc.pipeline.nodes.api.frame

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer

/**
 * Статическая топология регистровой памяти, вычисленная компилятором для конкретного конвейера.
 */
class FrameLayout(
    val longSlots: Int,
    val doubleSlots: Int,
    val refSlots: Int,
    val textSlots: Int,
    val textCapacity: Int = 1024,
    val requiredInputMask: Long = 0L
) {
    companion object {
        const val MASK_INPUT_TITLE: Long = 1L shl 0
        const val MASK_INPUT_TEXT: Long = 1L shl 1
        const val MASK_INPUT_SENDER: Long = 1L shl 2
        const val MASK_INPUT_POST_TIME: Long = 1L shl 3
        const val MASK_INPUT_PACKAGE: Long = 1L shl 4
        const val MASK_INPUT_CHANNEL_ID: Long = 1L shl 5
    }
}

/**
 * Контекст исполнения конвейера для одного рабочего потока (Thread-Local или Coroutine Worker Pool).
 * Все поля являются публичными (@JvmField) массивами для обеспечения мономорфного доступа JIT-компилятора.
 */
class Frame(val layout: FrameLayout) {
    @JvmField val longs: LongArray = LongArray(layout.longSlots)
    @JvmField val doubles: DoubleArray = DoubleArray(layout.doubleSlots)
    @JvmField val refs: Array<Any?> = arrayOfNulls(layout.refSlots)
    @JvmField val texts: Array<TextRegister> = Array(layout.textSlots) { TextRegister(layout.textCapacity) }

    /** Предвыделенный буфер побочных эффектов. */
    @JvmField val effects: EffectBuffer = EffectBuffer(maxEffects = 32, maxArgs = 64, maxChars = 2048)

    /** Аппаратный счетчик шагов для защиты от зацикливания конвейера. */
    @JvmField var stepBudget: Int = 1000

    /**
     * Сброс ссылочных полей для предотвращения утечек памяти между событиями.
     * Примитивные массивы longs и doubles очищать не требуется: статический компилятор
     * гарантирует Definite Assignment перед каждым чтением слота.
     */
    fun resetRefs() {
        java.util.Arrays.fill(refs, null)
        for (i in texts.indices) {
            texts[i].clear()
        }
        effects.reset()
        stepBudget = 1000
    }
}
```

#### 2.2. Результат шага: `StepResult` (Inlined Value Class)

Метод `NodeExecutor.execute()` обязан возвращать результат без создания объектов в Heap. Для этого используется `@JvmInline value class StepResult(val bits: Long)`, упаковывающий код операции (32 бита) и целочисленный аргумент (32 бита) в один примитивный 64-битный регистр процессора:

```kotlin
package com.example.npc.pipeline.nodes.api.executor

/**
 * Высокопроизводительный безаллокационный результат исполнения шага узла.
 * Упакован в 64-битный примитив Long:
 * - Старшие 32 бита: код операции [op] (NEXT, JUMP, HALT, FAIL).
 * - Младшие 32 бита: целочисленный аргумент [arg] (целевой PC, код завершения, код ошибки).
 */
@JvmInline
value class StepResult private constructor(val bits: Long) {
    val op: Int get() = (bits ushr 32).toInt()
    val arg: Int get() = bits.toInt()

    val isNext: Boolean get() = op == OP_NEXT
    val isJump: Boolean get() = op == OP_JUMP
    val isHalt: Boolean get() = op == OP_HALT
    val isFail: Boolean get() = op == OP_FAIL

    companion object {
        const val OP_NEXT: Int = 0
        const val OP_JUMP: Int = 1
        const val OP_HALT: Int = 2
        const val OP_FAIL: Int = 3

        /** Переход к следующему последовательному узлу в программе (PC + 1). */
        val NEXT: StepResult = StepResult(OP_NEXT.toLong() shl 32)

        /** Безусловный переход по указанному программному счетчику [targetPc]. */
        fun jump(targetPc: Int): StepResult =
            StepResult((OP_JUMP.toLong() shl 32) or (targetPc.toLong() and 0xFFFF_FFFFL))

        /**
         * Успешное штатное завершение конвейера (Early Exit / Terminal Action).
         * @param reason Код причины (например, REASON_DROPPED = 1, REASON_MATCHED = 2).
         */
        fun halt(reason: Int = 0): StepResult =
            StepResult((OP_HALT.toLong() shl 32) or (reason.toLong() and 0xFFFF_FFFFL))

        /**
         * Сбой исполнения узла с указанием кода ошибки [errorCode].
         */
        fun fail(errorCode: Int): StepResult =
            StepResult((OP_FAIL.toLong() shl 32) or (errorCode.toLong() and 0xFFFF_FFFFL))
    }
}
```

#### 2.3. Исполнитель узла: `NodeExecutor`

```kotlin
package com.example.npc.pipeline.nodes.api.executor

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame

/**
 * Исполняемый контракт скомпилированного узла на горячем пути.
 * Экземпляры NodeExecutor иммутабельны, потокобезопасны и могут переиспользоваться
 * множеством параллельных рабочих потоков.
 */
fun interface NodeExecutor {
    /**
     * Выполняет обработку события над регистровым фреймом.
     *
     * @param frame Рабочий регистровый фрейм текущего потока.
     * @param buffer Предвыделенный буфер для постановки побочных эффектов.
     * @return [StepResult] с инструкцией для диспетчера рантайма.
     */
    fun execute(frame: Frame, buffer: EffectBuffer): StepResult
}
```

#### 2.4. Буфер отложенных эффектов: `EffectBuffer`

Действия над внешними подсистемами (Room, NotificationManager, Outbox шины) буферизуются в `EffectBuffer`. Буфер спроектирован по модели Struct-of-Arrays (SoA) на плоских предвыделенных массивах:

```kotlin
package com.example.npc.pipeline.nodes.api.effect

import com.example.npc.pipeline.nodes.api.frame.TextRegister

/**
 * Высокопроизводительный предвыделенный буфер побочных эффектов.
 * Предоставляет поддержку контрольных точек (Savepoints) через [mark] и [rollback]
 * для локализации сбоев отдельных узлов конвейера.
 */
class EffectBuffer(
    val maxEffects: Int = 32,
    val maxArgs: Int = 64,
    val maxChars: Int = 2048
) {
    @JvmField internal val kindCodes: IntArray = IntArray(maxEffects)
    @JvmField internal val originPcs: IntArray = IntArray(maxEffects)
    @JvmField internal val argStarts: IntArray = IntArray(maxEffects + 1)
    @JvmField internal val longArgs: LongArray = LongArray(maxArgs)
    @JvmField internal val refArgs: Array<Any?> = arrayOfNulls(maxArgs)
    @JvmField internal val textChars: CharArray = CharArray(maxChars)

    @JvmField var effectCount: Int = 0
    @JvmField var argTop: Int = 0
    @JvmField var charTop: Int = 0

    /** Устанавливается диспетчером перед вызовом execute каждого узла */
    @JvmField var currentPc: Int = 0

    /** Битовая маска разрешенных для текущего узла эффектов (enforced sandbox) */
    @JvmField var allowMask: Long = -1L

    /**
     * Открывает запись нового эффекта.
     * @param kindCode Числовой идентификатор эффекта из EffectRegistry.
     * @return true если эффект разрешен маской и в буфере есть свободное место.
     */
    fun begin(kindCode: Int): Boolean {
        if ((allowMask and (1L shl kindCode)) == 0L) return false // Попытка эмиссии необъявленного эффекта
        if (effectCount >= maxEffects) return false // Переполнение буфера эффектов
        kindCodes[effectCount] = kindCode
        originPcs[effectCount] = currentPc
        argStarts[effectCount] = argTop
        return true
    }

    fun putLong(value: Long): Boolean {
        if (argTop >= maxArgs) return false
        longArgs[argTop++] = value
        return true
    }

    fun putRef(value: Any?): Boolean {
        if (argTop >= maxArgs) return false
        refArgs[argTop++] = value
        return true
    }

    fun putText(text: TextRegister): Boolean {
        if (argTop >= maxArgs) return false
        val len = text.len
        if (charTop + len > maxChars) return false
        val offset = charTop
        System.arraycopy(text.chars, 0, textChars, offset, len)
        charTop += len
        // Упаковываем смещение (старшие 32 бита) и длину (младшие 32 бита) в longArgs
        longArgs[argTop++] = (offset.toLong() shl 32) or (len.toLong() and 0xFFFF_FFFFL)
        return true
    }

    fun end() {
        effectCount++
        argStarts[effectCount] = argTop
    }

    /**
     * Создает легковесный снимок состояния буфера (контрольную точку).
     */
    fun mark(): Long {
        return (effectCount.toLong() shl 42) or (argTop.toLong() shl 21) or (charTop.toLong() and 0x1FFFFFL)
    }

    /**
     * Мгновенный откат всех эффектов, добавленных после создания снимка [mark].
     */
    fun rollback(mark: Long) {
        effectCount = (mark ushr 42).toInt()
        argTop = ((mark ushr 21) and 0x1FFFFF).toInt()
        charTop = (mark and 0x1FFFFF).toInt()
        java.util.Arrays.fill(refArgs, argTop, refArgs.size, null)
    }

    fun reset() {
        effectCount = 0
        argTop = 0
        charTop = 0
        java.util.Arrays.fill(refArgs, null)
    }

    /**
     * Формирует неизменяемое представление для фазы фиксации (Commit Phase).
     */
    fun view(): EffectView = EffectView(this)
}

/**
 * Итератор по буферу эффектов на фазе фиксации (Commit Phase).
 */
class EffectView internal constructor(private val buf: EffectBuffer) {
    val size: Int get() = buf.effectCount

    fun getKind(index: Int): Int = buf.kindCodes[index]
    fun getOriginPc(index: Int): Int = buf.originPcs[index]
    
    fun getLongArg(effectIndex: Int, argOffset: Int): Long {
        val start = buf.argStarts[effectIndex]
        return buf.longArgs[start + argOffset]
    }

    fun getRefArg(effectIndex: Int, argOffset: Int): Any? {
        val start = buf.argStarts[effectIndex]
        return buf.refArgs[start + argOffset]
    }

    fun getTextArg(effectIndex: Int, argOffset: Int): String {
        val start = buf.argStarts[effectIndex]
        val packed = buf.longArgs[start + argOffset]
        val offset = (packed ushr 32).toInt()
        val len = packed.toInt()
        return String(buf.textChars, offset, len)
    }
}
```

##### 2.4.1. Реестр типов эффектов и политики слияния (`MergePolicy`)

```kotlin
package com.example.npc.pipeline.nodes.api.effect

@JvmInline
value class EffectKindId(val value: String) {
    override fun toString(): String = value
}

enum class MergePolicy {
    /** При наличии нескольких эффектов одного типа применяется последний по порядку выполнения. */
    LAST_WINS,

    /** Применяется первый сработавший эффект, последующие игнорируются. */
    FIRST_WINS,

    /** Все эффекты данного типа аккумулируются в список (например, добавление тегов/логов). */
    ACCUMULATE,

    /** При наличии более одного эффекта данного типа фиксируется ошибка конфликта конвейера. */
    FAIL_ON_CONFLICT
}

data class EffectKindDef(
    val id: EffectKindId,
    val mergePolicy: MergePolicy = MergePolicy.LAST_WINS,
    val description: String = ""
)
```

#### 2.5. Спецификация узла: `NodeSpec`

`NodeSpec` — главный контракт уровня компиляции. Он связывает декларативное представление DSL (AST) с низкоуровневой регистровой машиной `Frame`.

```kotlin
package com.example.npc.pipeline.nodes.api.spec

import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor

@JvmInline
value class NodeTypeId(val value: String) {
    override fun toString(): String = value
}

enum class NodeCategory {
    CONDITION,
    TRANSFORM,
    ACTION
}

data class NodeTraits(
    /** Чистая функция без побочных эффектов. Детерминирована, может подвергаться Constant Folding. */
    val pure: Boolean = false,
    /** Может изменять Program Counter (возвращать StepResult.jump). */
    val mayJump: Boolean = false,
    /** Относительная вычислительная сложность узла (1 = тривиальная проверка, 10 = тяжелый regex/хеш). */
    val costRating: Int = 1
)

interface NodeSpec {
    /** Уникальный идентификатор типа узла в каталоге (например, "condition.package_match"). */
    val id: NodeTypeId

    /** Версия спецификации узла для обеспечения обратной совместимости при миграциях. */
    val version: Int

    /** Категория узла: Условие, Трансформация или Действие. */
    val category: NodeCategory

    /** Человекочитаемое имя для палитры UI редактора. */
    val displayName: String

    /** Описание назначения узла для документации и тултипов UI. */
    val description: String

    /** Декларативная схема параметров конфигурации. */
    val paramsSchema: ParamSchema

    /** Архитектурные характеристики узла. */
    val traits: NodeTraits

    /** Множество эффектов, которые данный узел имеет право порождать. */
    val declaredEffects: Set<EffectKindId> get() = emptySet()

    /**
     * Вычисляет схему входных и выходных портов узла на основе переданных параметров.
     */
    fun resolvePorts(params: ParamValues, diag: Diagnostics): PortSchema

    /**
     * Семантическая валидация параметров узла на этапе компиляции.
     */
    fun validate(params: ParamValues, diag: Diagnostics) {}

    /**
     * Спецификация предвыделенных рабочих объектов (Scratch objects),
     * создаваемых ровно один раз на каждый Frame (например, Matcher RE2/J).
     */
    fun declareScratch(params: ParamValues): List<ScratchSpec> = emptyList()

    /**
     * Фабрика создания исполнителя. Вызывается ровно один раз на этапе компиляции конвейера.
     * Все тяжелые операции (парсинг шаблонов, аллокации массивов констант) выполняются здесь.
     */
    fun bind(ctx: BindContext): NodeExecutor
}
```

##### 2.5.1. Контракты портов и параметров: `PortSchema`, `ParamSchema`, `BindContext`

```kotlin
package com.example.npc.pipeline.nodes.api.spec

import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.frame.Bank

data class PortDef(
    val name: String,
    val bank: Bank,
    val isRequired: Boolean = true,
    val failureDefaultLong: Long = 0L,
    val failureDefaultDouble: Double = 0.0,
    val failureDefaultRef: Any? = null
)

class PortSchema(
    val inputs: List<PortDef> = emptyList(),
    val outputs: List<PortDef> = emptyList(),
    val branches: List<String> = emptyList() // Именованные ветки переходов: "then", "else"
)

sealed interface ParamType {
    object StringType : ParamType
    object LongType : ParamType
    object DoubleType : ParamType
    object BooleanType : ParamType
    data class StringListType(val minItems: Int = 0, val maxItems: Int = 100) : ParamType
    data class EnumType(val allowedValues: List<String>) : ParamType
}

data class ParamDef<T>(
    val name: String,
    val type: ParamType,
    val defaultValue: T? = null,
    val isRequired: Boolean = true,
    val description: String = ""
)

class ParamSchema(val params: List<ParamDef<*>>) {
    constructor(vararg defs: ParamDef<*>) : this(defs.toList())
}

interface ParamValues {
    fun getString(name: String, default: String = ""): String
    fun getLong(name: String, default: Long = 0L): Long
    fun getDouble(name: String, default: Double = 0.0): Double
    fun getBoolean(name: String, default: Boolean = false): Boolean
    fun getStringList(name: String): List<String>
    fun has(name: String): Boolean
}

interface ScratchSpec {
    val key: String
    fun createInstance(): Any
}

interface BindContext {
    /** Разрешает имя входного порта в индекс слота соответствующего банка */
    fun resolveInputSlot(portName: String): Int

    /** Разрешает имя выходного порта в индекс слота соответствующего банка */
    fun resolveOutputSlot(portName: String): Int

    /** Разрешает имя ветвления ("then", "else") в абсолютный программный счетчик (PC) */
    fun resolveBranchPc(branchName: String): Int

    /** Возвращает проверенные декодированные параметры узла */
    val params: ParamValues

    /** Разрешает индекс в массиве refs для предвыделенного scratch-объекта */
    fun resolveScratchSlot(key: String): Int

    /** Разрешает строковый ID эффекта в числовой код для EffectBuffer */
    fun resolveEffectCode(effectId: EffectKindId): Int

    /** Индекс следующего шага программы (PC + 1) */
    val nextPc: Int
}

interface Diagnostics {
    fun reportError(message: String, paramName: String? = null)
    fun reportWarning(message: String, paramName: String? = null)
}
```

#### 2.6. Каталог и реестр узлов: `NodeRegistry`

`NodeRegistry` представляет собой потокобезопасный реестр зарегистрированных спецификаций `NodeSpec` и определений эффектов `EffectKindDef`.
- **Явная сборка:** Регистрация производится программно через `Builder` без медленного `java.util.ServiceLoader`, гарантируя детерминизм и совместимость с R8/ProGuard.
- **Fingerprinting каталога:** Реестр вычисляет 64-битный хэш `fingerprint` от всех зарегистрированных типов и их версий. Этот хэш штампуется в скомпилированный конвейер. При изменении набора узлов устаревшие скомпилированные пайплайны инвалидируются и автоматически перекомпилируются.

```kotlin
package com.example.npc.pipeline.nodes.api.registry

import com.example.npc.pipeline.nodes.api.effect.EffectKindDef
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.spec.NodeCategory
import com.example.npc.pipeline.nodes.api.spec.NodeSpec
import com.example.npc.pipeline.nodes.api.spec.NodeTypeId

interface NodeModule {
    fun contribute(builder: NodeRegistry.Builder)
}

class NodeRegistry private constructor(
    private val specs: Map<NodeTypeId, NodeSpec>,
    private val effects: Map<EffectKindId, EffectKindDef>,
    val catalogFingerprint: Long
) {
    fun getSpec(typeId: NodeTypeId): NodeSpec? = specs[typeId]
    fun requireSpec(typeId: NodeTypeId): NodeSpec = specs[typeId]
        ?: throw IllegalArgumentException("Unknown node typeId: '${typeId.value}'")

    fun getAllSpecs(): Collection<NodeSpec> = specs.values
    fun getSpecsByCategory(category: NodeCategory): List<NodeSpec> =
        specs.values.filter { it.category == category }

    fun getEffectDef(effectId: EffectKindId): EffectKindDef? = effects[effectId]
    fun getAllEffects(): Collection<EffectKindDef> = effects.values

    class Builder {
        private val specs = LinkedHashMap<NodeTypeId, NodeSpec>()
        private val effects = LinkedHashMap<EffectKindId, EffectKindDef>()

        fun register(spec: NodeSpec): Builder = apply {
            require(!specs.containsKey(spec.id)) { "Node with typeId '${spec.id.value}' is already registered!" }
            specs[spec.id] = spec
        }

        fun registerEffect(effect: EffectKindDef): Builder = apply {
            effects[effect.id] = effect
        }

        fun install(module: NodeModule): Builder = apply {
            module.contribute(this)
        }

        fun build(): NodeRegistry {
            // Верификация: все объявленные в спецификациях эффекты должны быть зарегистрированы
            for (spec in specs.values) {
                for (eff in spec.declaredEffects) {
                    require(effects.containsKey(eff)) {
                        "Node '${spec.id.value}' declares unregistered effect '${eff.value}'"
                    }
                }
            }

            // Вычисление детерминированного 64-битного отпечатка каталога
            var fp = 1125899906842597L // FNV offset basis
            for ((id, spec) in specs) {
                fp = fp xor id.value.hashCode().toLong()
                fp = fp * 1099511628211L
                fp = fp xor spec.version.toLong()
                fp = fp * 1099511628211L
            }
            for ((effId, _) in effects) {
                fp = fp xor effId.value.hashCode().toLong()
                fp = fp * 1099511628211L
            }

            return NodeRegistry(specs, effects, fp)
        }
    }
}
```

---

### 3. Каталог встроенных узлов (`:pipeline:nodes-builtin`)

Модуль `:pipeline:nodes-builtin` поставляет 14 высокопроизводительных встроенных узлов, покрывающих 100% функциональности эталонного конвейера Фазы 1.1 («Legacy 1.1»).

```
:pipeline:nodes-builtin
├── BuiltinNodesModule.kt              # Точка входа регистрации всех встроенных узлов
├── BuiltinEffects.kt                  # Константы эффектов (SET_CATEGORY, CREATE_TRANSACTION, DROP, STORAGE)
├── condition/
│   ├── PackageConditionSpec.kt        # Проверка пакета (Allowlist, EXACT, PREFIX, GLOB, CONTAINS)
│   ├── SenderConditionSpec.kt         # Проверка отправителя/заголовка
│   ├── TextRegexConditionSpec.kt      # RE2/J проверка текста по паттерну
│   ├── CategoryConditionSpec.kt       # Проверка текущей категории события и уверенности
│   └── PrototypeConditionSpec.kt      # Проверка supportCount >= 2 по базе прототипов
├── transform/
│   ├── FingerprinterTransformSpec.kt  # O(n) шаблонизация и SHA-256 хэширование отпечатка
│   ├── RegionalTextSanitizerSpec.kt   # Очистка диакритик, NBSP, усечение 1024
│   ├── AmountParserTransformSpec.kt   # Ручной O(n) парсер копеек/центов в Long
│   ├── BankCurrencyResolverSpec.kt    # Контекстный резолвер RUP / MDL / RUB / EUR / USD
│   └── FinanceExtractorSpec.kt        # Запуск APB, MAIB, Prisbank, BankSMS в песочнице
└── action/
    ├── SetCategoryActionSpec.kt       # Буферизация назначения категории
    ├── CreateTransactionActionSpec.kt # Буферизация финансовой транзакции
    ├── DropEventActionSpec.kt         # Сигнал подавления спама
    └── SaveToStorageActionSpec.kt     # Фиксация в Room
```

#### 3.1. Условия (Conditions)

##### 3.1.1. `PackageConditionSpec`
- **TypeId:** `"condition.package"`
- **Категория:** `NodeCategory.CONDITION`
- **Назначение:** Аппаратная проверка имени Android-пакета источника по списку или предикату. Защищает финансовый контур от мессенджеров (AyuGram, Telegram, WhatsApp).
- **Параметры:**
  * `packages`: `StringList` — список разрешенных/проверяемых пакетов.
  * `matchMode`: `Enum("EXACT", "PREFIX", "GLOB", "CONTAINS")` (по умолчанию `"EXACT"`).
  * `negate`: `Boolean` — инвертировать результат проверки (по умолчанию `false`).
- **Порты:**
  * Вход: `in_package` (`Bank.TEXT`).
  * Ветвления: `then` (соответствует), `else` (не соответствует).
- **Реализация Executor (Zero Allocation):**

```kotlin
class PackageConditionExecutor(
    private val inPackageSlot: Int,
    private val allowedPackages: Array<String>,
    private val matchMode: Int,
    private val negate: Boolean,
    private val thenPc: Int,
    private val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        val pkg = frame.texts[inPackageSlot]
        var matched = false
        val pkgLen = pkg.len
        val count = allowedPackages.size

        for (i in 0 until count) {
            val target = allowedPackages[i]
            val targetLen = target.length
            when (matchMode) {
                MODE_EXACT -> {
                    if (pkgLen == targetLen && matchesRange(pkg, target, 0, targetLen)) {
                        matched = true
                        break
                    }
                }
                MODE_PREFIX -> {
                    if (pkgLen >= targetLen && matchesRange(pkg, target, 0, targetLen)) {
                        matched = true
                        break
                    }
                }
                MODE_CONTAINS -> {
                    if (indexOf(pkg, target) >= 0) {
                        matched = true
                        break
                    }
                }
            }
        }

        if (negate) matched = !matched
        return StepResult.jump(if (matched) thenPc else elsePc)
    }

    private fun matchesRange(text: TextRegister, target: String, offset: Int, len: Int): Boolean {
        for (i in 0 until len) {
            if (text.chars[offset + i] != target[i]) return false
        }
        return true
    }

    private fun indexOf(text: TextRegister, target: String): Int {
        val max = text.len - target.length
        for (i in 0..max) {
            var found = true
            for (j in 0 until target.length) {
                if (text.chars[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    companion object {
        const val MODE_EXACT = 0
        const val MODE_PREFIX = 1
        const val MODE_CONTAINS = 2
    }
}
```

##### 3.1.2. `SenderConditionSpec`
- **TypeId:** `"condition.sender"`
- **Параметры:** `senders: StringList`, `caseSensitive: Boolean`, `negate: Boolean`.
- **Порты:** Вход `in_sender` (`Bank.TEXT`), ветвления `then`, `else`.
- **Реализация:** Посимвольное сопоставление имени отправителя со списком доверенных банковских SMS-отправителей (`"APB"`, `"PRISBANK"`, `"MAIB"`, `"900"`) без аллокаций.

##### 3.1.3. `TextRegexConditionSpec`
- **TypeId:** `"condition.text_regex"`
- **Параметры:** `pattern: String` (макс. 256 символов), `caseSensitive: Boolean`.
- **Scratch:** Регистрирует предвыделенный экземпляр `com.google.re2j.Matcher`.
- **Порты:** Вход `in_text` (`Bank.TEXT`), ветвления `then`, `else`.
- **Реализация:**
  ```kotlin
  class TextRegexConditionExecutor(
      private val inTextSlot: Int,
      private val scratchMatcherSlot: Int,
      private val thenPc: Int,
      private val elsePc: Int
  ) : NodeExecutor {
      override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
          val text = frame.texts[inTextSlot]
          val matcher = frame.refs[scratchMatcherSlot] as com.google.re2j.Matcher
          matcher.reset(text) // TextRegister реализует CharSequence, 0 байт аллокаций!
          val matched = matcher.find()
          return StepResult.jump(if (matched) thenPc else elsePc)
      }
  }
  ```

##### 3.1.4. `CategoryConditionSpec`
- **TypeId:** `"condition.category"`
- **Параметры:** `expectedCategory: String`, `minConfidence: Double`.
- **Порты:** Входы `in_category_ordinal` (`Bank.LONG`), `in_confidence` (`Bank.DOUBLE`), ветвления `then`, `else`.
- **Реализация:** Сравнение ординала категории и порога уверенности через примитивные регистры.

##### 3.1.5. `PrototypeConditionSpec`
- **TypeId:** `"condition.prototype_feedback"`
- **Параметры:** `minSupportCount: Long` (по умолчанию `2L`).
- **Порты:** Вход `in_fingerprint` (`Bank.REF` — строка хэша), входы `in_package` (`Bank.TEXT`), выходы `out_category` (`Bank.LONG`), `out_confidence` (`Bank.DOUBLE`), ветвления `then`, `else`.
- **Интеграция:** Обращается к injected read-only снапшоту базы прототипов `PrototypeSnapshotTable`. Если отпечаток найден и `supportCount >= minSupportCount`, записывает категорию в регистры и переходит по ветке `then`.

---

#### 3.2. Трансформации (Transforms)

##### 3.2.1. `FingerprinterTransformSpec`
- **TypeId:** `"transform.fingerprinter"`
- **Назначение:** Очистка текста от переменных данных (суммы, даты, номера карт) и вычисление канонического хэша отпечатка шаблона уведомления.
- **Алгоритм:** Потоковый алгоритм $O(n)$ заменяет цифры `[0-9]` на токен `#`, распознает даты/время `##.##.####` $\to$ `<DATE>`, убирает случайные идентификаторы и вычисляет SHA-256 хеш шаблона.
- **Порты:** Вход `in_text` (`Bank.TEXT`), выход `out_fingerprint` (`Bank.REF`).
- **Реализация:** Использует предвыделенный в scratch массив байтов и `MessageDigest.getInstance("SHA-256")`, переиспользуемый через `digest.reset()`.

##### 3.2.2. `RegionalTextSanitizerTransformSpec`
- **TypeId:** `"transform.regional_sanitizer"`
- **Параметры:** `maxChars: Long = 1024`, `normalizeNbsp: Boolean = true`, `stripDiacritics: Boolean = false`.
- **Порты:** Вход `in_text` (`Bank.TEXT`), выход `out_text` (`Bank.TEXT`).
- **Алгоритм:**
  - Заменяет неразрывные пробелы NBSP (`\u00A0`), Narrow NBSP (`\u202F`), Zero-width spaces (`\u200B`) на стандартный пробел `0x20`.
  - Удаляет непечатаемые нулевые символы `\u0000`.
  - Жестко ограничивает длину до 1024 символов (защита от ReDoS и раздувания памяти).

##### 3.2.3. `AmountParserTransformSpec`
- **TypeId:** `"transform.amount_parser"`
- **Назначение:** Высокоскоростной рукописный синтаксический анализатор сумм без регулярных выражений.
- **Алгоритм:** Посимвольный автомат $O(n)$ ищет последовательности цифр с десятичными разделителями (`,`, `.`). Преобразует сумму в минимальные неделимые единицы (`Long` minor units, копейки/центы), исключая ошибки округления `Double`.
- **Порты:** Вход `in_text` (`Bank.TEXT`), выход `out_amount_minor` (`Bank.LONG`), выход `out_found` (`Bank.LONG`, 1L/0L).

##### 3.2.4. `BankCurrencyResolverTransformSpec`
- **TypeId:** `"transform.currency_resolver"`
- **Параметры:** `defaultCurrency: String = "RUP"`.
- **Порты:** Вход `in_text` (`Bank.TEXT`), вход `in_package` (`Bank.TEXT`), выход `out_currency_code` (`Bank.LONG` — ordinal Enum CurrencyCode).
- **Алгоритм:**
  - Контекстный анализ: если пакет `com.apb.mobile` или `com.prisbank.app`, токены `"руб."`, `"р."`, `"R"` однозначно резолвятся как `RUP` (Приднестровский рубль).
  - Для молдавского банка `md.maib.maibank` токены резолвятся в `MDL`, `EUR`, `USD`.
  - Предотвращает критическую ошибку смешивания RUP с российским рублем RUB.

##### 3.2.5. `FinanceExtractorTransformSpec`
- **TypeId:** `"transform.finance_extractor"`
- **Параметры:** `extractorId: String = "auto"`, `timeoutMs: Long = 50L`.
- **Порты:** Вход `in_text` (`Bank.TEXT`), вход `in_package` (`Bank.TEXT`), вход `in_sender` (`Bank.TEXT`), выход `out_transaction_ref` (`Bank.REF`), выход `out_success` (`Bank.LONG`).
- **Изоляция:** Запускает банковские экстракторы (APB, MAIB, Prisbank, BankSMS) внутри защищенного контура с замером наносекунд и перехватом ошибок. При превышении бюджета 50 мс или выбросе исключения срабатывает локальный предохранитель `CircuitBreaker`.

---

#### 3.3. Действия (Actions)

Все узлы действий **не производят прямых записей в БД**, а регистрируют структурированные записи в `EffectBuffer`.

```
                    ┌────────────────────────┐
                    │      Action Node       │
                    └───────────┬────────────┘
                                │
                    buffer.begin(KIND_CODE)
                    buffer.putLong(value)
                    buffer.putText(textReg)
                    buffer.end()
                                │
                                ▼
                    ┌────────────────────────┐
                    │      EffectBuffer      │ (Staged in RAM)
                    └───────────┬────────────┘
                                │
                      Конвейер завершился?
                        /               \
                 PASS  /                 \  DROP / FAULT
                      ▼                   ▼
           ┌──────────────────────┐   ┌──────────────────────┐
           │ Commit Phase:        │   │ buffer.reset()       │
           │ StorageGateway.apply │   │ Ноль побочных данных │
           └──────────────────────┘   └──────────────────────┘
```

##### 3.3.1. `SetCategoryActionSpec`
- **TypeId:** `"action.set_category"`
- **Параметры:** `category: String`, `confidence: Double = 1.0`, `engine: String = "RULES"`.
- **Declared Effects:** `BuiltinEffects.SET_CATEGORY` (MergePolicy: `LAST_WINS`).
- **Реализация:**
  ```kotlin
  class SetCategoryActionExecutor(
      private val categoryOrdinal: Long,
      private val confidenceBits: Long,
      private val engineOrdinal: Long,
      private val effectCode: Int,
      private val nextPc: Int
  ) : NodeExecutor {
      override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
          if (buffer.begin(effectCode)) {
              buffer.putLong(categoryOrdinal)
              buffer.putLong(confidenceBits)
              buffer.putLong(engineOrdinal)
              buffer.end()
          }
          return StepResult.NEXT
      }
  }
  ```

##### 3.3.2. `CreateTransactionActionSpec`
- **TypeId:** `"action.create_transaction"`
- **Параметры:** `direction: String? = null`, `status: String = "COMPLETED"`.
- **Declared Effects:** `BuiltinEffects.CREATE_TRANSACTION` (MergePolicy: `FAIL_ON_CONFLICT`).
- **Порты:** Вход `in_amount_minor` (`Bank.LONG`), вход `in_currency_code` (`Bank.LONG`), вход `in_tx_ref` (`Bank.REF`).
- **Реализация:** Записывает сумму, валюту и ссылку на детали транзакции в буфер эффектов.

##### 3.3.3. `DropEventActionSpec`
- **TypeId:** `"action.drop_event"`
- **Параметры:** `reason: String`.
- **Declared Effects:** `BuiltinEffects.DROP_EVENT` (MergePolicy: `LAST_WINS`).
- **Реализация:**
  ```kotlin
  class DropEventActionExecutor(
      private val reasonCode: Int,
      private val effectCode: Int
  ) : NodeExecutor {
      override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
          if (buffer.begin(effectCode)) {
              buffer.putLong(reasonCode.toLong())
              buffer.end()
          }
          // Немедленно останавливает конвейер сигналом HALT
          return StepResult.halt(reasonCode)
      }
  }
  ```

##### 3.3.4. `SaveToStorageActionSpec`
- **TypeId:** `"action.save_to_storage"`
- **Параметры:** `completeProcessing: Boolean = true`.
- **Declared Effects:** `BuiltinEffects.SAVE_TO_STORAGE` (MergePolicy: `LAST_WINS`).
- **Реализация:** Помещает маркер необходимости сохранения нормализованного события в Room с отметкой завершения обработки.

---

### 4. Безопасность, отказоустойчивость и изоляция сбоев

#### 4.1. Двухуровневая изоляция сбоев узла (Failure Isolation Protocol)

Если при исполнении узла происходит непредвиденный сбой (арифметическое деление на ноль, выход за границы массива в сторонней библиотеке, сбой парсинга), исполняющий рантайм гарантирует локализацию ошибки без падения хост-процесса NLS.

```
       [Шаг узла: execute(frame, buffer)]
                      │
           try { node.execute() }
                      │
           ┌──────────┴──────────┐
      УСПЕХ                   THROWABLE
           │                     │
      StepResult.NEXT            ├── 1. VirtualMachineError? ──▶ throw (OOM / SOE)
      или JUMP                   │
                                 ├── 2. Откат эффектов: buffer.rollback(savepointMark)
                                 ├── 3. Запись в логи: failures.record(pc, throwable)
                                 ├── 4. Инкремент счетчика сбоев узла: failCounters[pc]++
                                 ├── 5. Применение дефолтов портов: applyPortDefaults(frame)
                                 │
                                 ▼
                     Политика узла (FailurePolicy)
                     ├── ABORT ──▶ return RunOutcome.ABORTED (Safe passthrough)
                     ├── SKIP  ──▶ pc++ (Пропуск узла с дефолтными выходами)
                     └── ROUTE ──▶ pc = errorBranchPc (Переход на ветку спасения)
```

1. **Фатальные системные ошибки JVM:** `VirtualMachineError` (`OutOfMemoryError`, `StackOverflowError`) **никогда не перехватываются** и немедленно пробрасываются выше.
2. **Откат побочных эффектов:** Перед вызовом `node.execute()` рантайм фиксирует контрольную точку буфера `savepointMark = buffer.mark()`. При сбое вызывается `buffer.rollback(savepointMark)`, что полностью стирает любые частичные эффекты упавшего узла.
3. **Безопасные дефолты выходов (`failureDefault`):** Значения выходных портов узла перезаписываются заранее зафиксированными компилятором дефолтными значениями (`0L`, `0.0`, `null`). Последующие узлы гарантированно не увидят мусора в регистрах.
4. **Политики обработки сбоя узла (`FailurePolicy`):**
   - `ABORT` (по умолчанию): Конвейер немедленно прерывает исполнение. Никакие эффекты не коммитятся. Исходное уведомление проходит на выход без изменений (Fail Closed).
   - `SKIP`: Узел пропускается, управление передается следующему шагу программы (`pc + 1`).
   - `ROUTE`: Управление передается по резервному программному счетчику обработки ошибок (`errorBranchPc`).

#### 4.2. Предохранитель узла (CircuitBreaker)

Для узлов с повышенным риском сбоев (например, `FinanceExtractor`):
- Каждый скомпилированный узел снабжается атомарным счетчиком последовательных ошибок `AtomicInteger`.
- При превышении порога ($N \ge 3$ сбоев подряд) узел переводится в состояние `OPEN` на период охлаждения (10 минут).
- В состоянии `OPEN` узел мгновенно возвращает `failureDefault` без запуска внутренней логики, предотвращая деградацию времени отклика системы.

#### 4.3. Аппаратная защита от зацикливания (Anti-Loop Protection)

1. **Ацикличность на уровне компиляции:** Компилятор валидирует граф переходов и разрешает переходы ветвлений строго вперед ($\text{targetPc} > \text{currentPc}$).
2. **Счетчик шагов исполнения (`stepBudget`):**
   - На каждое входящее событие выделяется жесткий бюджет операций: `frame.stepBudget = 1000`.
   - В цикле горячего рантайма счетчик декрементируется на каждой итерации:
     ```kotlin
     if (--frame.stepBudget < 0) {
         return RunOutcome.ABORTED_BUDGET // Принудительный сброс при зацикливании
     }
     ```
3. **Защита от циклического постинга уведомлений:** Встроенный узел триггера и действия проверяет собственный `packageName` приложения. Пуши, сгенерированные самим приложением, игнорируются на аппаратном уровне входного шлюза, что делает невозможным возникновение самовозбуждающихся петель уведомлений.

---

### 5. Тест-план и верификация

#### 5.1. Набор инвариантов контракта узла (`NodeContractSuite`)

Каждый узел из каталога `:pipeline:nodes-builtin` обязан пройти параметризованный верификационный люкс `NodeContractSuite`:

1. **Тест нулевых аллокаций памяти (Zero-Allocation Verification):**
   - Проводится прогрев JIT (10 000 итераций `execute`).
   - Замеряется дельта выделенной памяти через `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()` на выборке из 100 000 вызовов.
   - **Критерий приемки:** $\Delta \text{AllocatedBytes} == 0$. Любая аллокация на горячем пути валит сборку в CI.
2. **Тест чистоты и детерминизма (`pure == true`):**
   - Одинаковое начальное состояние регистров `Frame` обязано возвращать идентичные выходные значения и код `StepResult`.
   - Чистый узел не имеет права вызывать `buffer.begin()`.
3. **Тест соблюдения маски эффектов (`allowMask`):**
   - Попытка узла эмитировать необъявленный в `declaredEffects` тип эффекта блокируется буфером и возвращает ошибку.
4. **Тест дефолтов при сбое (`failureDefault`):**
   - Принудительное внедрение сбоя (Fault Injection) должно приводить к заполнению выходных портов точными дефолтными значениями.

#### 5.2. Модульные тесты встроенных узлов

| Узел | Тестовый сценарий | Ожидаемый результат |
|---|---|---|
| `PackageCondition` | Пакет `com.apb.mobile` против Allowlist `[com.apb.mobile, com.prisbank.app]` | Переход по ветке `then` |
| `PackageCondition` | Пакет `com.radolyn.ayugram` (Telegram) | Переход по ветке `else` (мессенджер заблокирован) |
| `SenderCondition` | Отправитель `"APB"` при caseSensitive = false против `"apb"` | Переход по ветке `then` |
| `TextRegexCondition` | Паттерн `(?i)оплата|покупка`, вход `"Оплата 120 RUP"` | Переход по `then`, 0 аллокаций |
| `RegionalTextSanitizer`| Строка `"Перевод\u00A0100\u202FRUP\u0000"` | Нормализация в `"Перевод 100 RUP"`, длина 15 |
| `AmountParser` | Вход `"Списание 1 250,50 RUP"` | `out_amount_minor = 125050L`, `out_found = 1L` |
| `AmountParser` | Вход `"Погода +10°C"` | `out_found = 0L` (ложные суммы отсечены) |
| `BankCurrencyResolver` | Пакет `com.apb.mobile`, токен `"руб."` | `CurrencyCode.RUP` (НЕ `RUB`!) |
| `BankCurrencyResolver` | Пакет `md.maib.maibank`, токен `"MDL"` | `CurrencyCode.MDL` |
| `DropEventAction` | Вызов действия с причиной `SPAM` | `StepResult.halt(1)`, буферизован `DROP_EVENT` |

#### 5.3. Интеграционный Golden-тест паритета «Legacy 1.1»

- **Тестовый корпус:** Все 23 реальные банковские транзакции из базы телеметрии Poco M7 (`event_engine.db`) плюс 114 реальных кейсов спама (InTour, Яндекс.Погода).
- **Схема конвейера:** Сборка пресета «Legacy 1.1» через `NodeRegistry.Builder` и компиляция в `CompiledPipeline`.
- **Критерий успеха:** 100% побитовое совпадение результатов классификации и финансовой экстракции между хардкод-оркестратором Фазы 1.1 и скомпилированным графом на базе встроенных узлов:
  * 0 ложноположительных срабатываний на спаме Telegram / AyuGram.
  * 23 из 23 финансовых транзакций распарсены с точностью до копейки/цента.
  * Категория спама туроператора InTour с `supportCount >= 2` безоговорочно подавляется через `PrototypeCondition`.

---

### 6. Бюджеты ресурсов и метрики производительности

Замеры производятся на эталонном устройстве Poco M7 (SoC начального уровня, ядра Cortex-A55, HyperOS):

| Метрика | Бюджет SLA | Типичное значение | Контроль |
|---|---|---|---|
| **Аллокация памяти в `execute()`** | **Строго 0 байт** | 0 байт | `ThreadMXBean` в CI тестах |
| **Время выполнения тривиального условия** (`PackageCondition`) | $\le 2$ мкс | $\approx 0.3$ мкс | JMH микро-бенчмарк |
| **Время выполнения текстового условия** (`TextRegexCondition` RE2/J) | $\le 15$ мкс | $\approx 3.5$ мкс | JMH микро-бенчмарк |
| **Время выполнения полного шага экстракции** (`FinanceExtractor`) | $\le 50$ мкс | $\approx 12$ мкс | Встроенный таймер узла |
| **Время сборки каталога `NodeRegistry.build()`** | $\le 2$ мс | $\approx 0.4$ мс | JUnit benchmark |
| **Память одного рабочего контекста `Frame`** | $\le 64$ КБ | $\approx 24$ КБ | Heap profiler |
| **Потокобезопасность `CompiledPipeline` и `NodeExecutor`** | Thread-safe | Многопоточный read-only | Конкурентный стресс-тест |

---

### 7. Сводная матрица трассируемости требований

| Требование Фазы 2 | Решение в архитектуре SPI | Модуль |
|---|---|---|
| G3: Каталог узлов через SPI | Интерфейсы `NodeSpec`, `ParamSchema`, `PortSchema`, каталог `NodeRegistry` | `:pipeline:nodes-api` |
| Zero Android JVM модель | Чистый Kotlin JVM без ссылок на Android SDK или Room | `:pipeline:nodes-api`, `:pipeline:nodes-builtin` |
| Zero Allocation Hot Path | Регистровая память `Frame`, `TextRegister`, `@JvmInline StepResult` | `:pipeline:nodes-api` |
| Двухфазный коммит эффектов | `EffectBuffer` с предвыделенными массивами и сейвпоинтами `mark`/`rollback` | `:pipeline:nodes-api` |
| Паритет с конвейером 1.1 | 14 встроенных узлов, повторяющих логику `:classify:rules` и `:extract:finance` | `:pipeline:nodes-builtin` |
| Защита от ReDoS | Линейный движок RE2/J с переиспользуемыми матчерами через scratch-слоты | `:pipeline:nodes-builtin` |
| Изоляция сбоев узла | Локальный перехват Throwable, `CircuitBreaker`, дефолты выходов `failureDefault` | `:pipeline:nodes-api` |
| Защита от зацикливания | Аппаратный декремент `stepBudget` во `Frame` и запрет обратных переходов | `:pipeline:nodes-api` |
