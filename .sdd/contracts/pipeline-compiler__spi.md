# Межзонный контракт: Pipeline Compiler ↔ Pipeline SPI Nodes API

**Версия:** FROZEN v3  
**Дата заморозки:** 2026-09-28  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Потребитель SPI узлов и генератор раскладки: `zone/pipeline-compiler` (`:pipeline:compiler`)
- Провайдер SPI интерфейсов и каталога узлов: `zone/pipeline-spi` (`:pipeline:nodes-api`)
- Среда исполнения (Hot Loop): `zone/pipeline-runtime` (`:pipeline:runtime`)

---

### 1. Архитектурный контекст и двухфазная модель

Контракт разделяет жизненный цикл узлов на две строгие фазы:
1. **Compile-time / Meta-Phase (холодный контур):**
   - Узел декларирует метаданные через `NodeSpec`, схему параметров `ParamSchema` и портов `PortSchema`.
   - Компилятор проверяет типизацию портов, валидирует параметры и производит привязку `bind(context: BindContext)`, выделяя статические слоты в `FrameLayout`.
2. **Runtime Hot Path (горячий контур исполнения):**
   - Узел исполняется как иммутабельный `NodeExecutor.execute(frame, buffer): StepResult`.
   - Строго **0 аллокаций памяти** в Heap на каждое входящее событие.
   - Прямой мономорфный доступ к предвыделенным плоским массивам в `Frame` и SoA-буферу `EffectBuffer`.

```
┌────────────────────────────────────────────────────────┐
│         zone/pipeline-compiler (:pipeline:compiler)    │
│  - Запрашивает NodeSpec из NodeRegistry                │
│  - Валидирует ParamSchema, рассчитывает PortSchema    │
│  - Выделяет статические смещения в FrameLayout         │
│  - Вызывает nodeSpec.bind(BindContext) -> NodeExecutor │
└───────────────────────────┬────────────────────────────┘
                            │ Регистрация / Привязка слотов
                            ▼
┌────────────────────────────────────────────────────────┐
│        zone/pipeline-spi (:pipeline:nodes-api)         │
│  - NodeSpec, ParamSchema, PortSchema, NodeRegistry     │
│  - FrameLayout, Frame, TextRegister, Bank              │
│  - StepResult (@JvmInline value class), NodeExecutor   │
│  - EffectBuffer (Struct-of-Arrays с mark/rollback)     │
└────────────────────────────────────────────────────────┘
```

---

### 2. Регистровая модель памяти (`Bank`, `TextRegister`, `FrameLayout`, `Frame`)

```kotlin
package com.example.npc.pipeline.nodes.api.frame

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer

/**
 * Банки регистровой памяти виртуальной машины.
 */
enum class Bank {
    /** 64-битные целые: Long, Int, Short, Byte, Boolean (0L/1L), Enum ordinals, EpochMillis. */
    LONG,

    /** 64-битные вещественные IEEE 754 (Double, Float bits). */
    DOUBLE,

    /** Ссылки на тяжелые хостовые DTO (FinancialTransaction, Category, UserPrototype) и scratch. */
    REF,

    /** Предвыделенные безаллокационные текстовые регистры с фиксированной емкостью. */
    TEXT
}

/**
 * Мутабельный строковый регистр фиксированной емкости.
 * Реализует [CharSequence] для передачи в RE2/J Matcher.reset() без аллокаций памяти.
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
        throw UnsupportedOperationException("subSequence allocates memory; inspect chars in-place")
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

    fun materializeString(): String = String(chars, 0, len)
}

/**
 * Топология регистровой памяти, вычисленная компилятором.
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
 * Рабочий контекст потока исполнения.
 * Поля представляют собой открытые (@JvmField) массивы для мономорфного доступа JIT.
 */
class Frame(val layout: FrameLayout) {
    @JvmField val longs: LongArray = LongArray(layout.longSlots)
    @JvmField val doubles: DoubleArray = DoubleArray(layout.doubleSlots)
    @JvmField val refs: Array<Any?> = arrayOfNulls(layout.refSlots)
    @JvmField val texts: Array<TextRegister> = Array(layout.textSlots) { TextRegister(layout.textCapacity) }

    @JvmField val effects: EffectBuffer = EffectBuffer(maxEffects = 32, maxArgs = 64, maxChars = 2048)
    @JvmField var stepBudget: Int = 1000

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

---

### 3. Контракты исполнения: `StepResult`, `NodeExecutor`, `EffectBuffer`

```kotlin
package com.example.npc.pipeline.nodes.api.executor

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame

/**
 * Безаллокационный упакованный результат выполнения узла (64-битный Long).
 * - Старшие 32 бита: код операции [op] (NEXT, JUMP, HALT, FAIL).
 * - Младшие 32 бита: целочисленный аргумент [arg] (targetPc, reason, errorCode).
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

        val NEXT: StepResult = StepResult(OP_NEXT.toLong() shl 32)
        fun jump(targetPc: Int): StepResult = StepResult((OP_JUMP.toLong() shl 32) or (targetPc.toLong() and 0xFFFF_FFFFL))
        fun halt(reason: Int = 0): StepResult = StepResult((OP_HALT.toLong() shl 32) or (reason.toLong() and 0xFFFF_FFFFL))
        fun fail(errorCode: Int): StepResult = StepResult((OP_FAIL.toLong() shl 32) or (errorCode.toLong() and 0xFFFF_FFFFL))
    }
}

/**
 * Интерфейс исполнения скомпилированного узла на горячем пути.
 * Должен быть потокобезопасным, чистым и не производить аллокаций в Heap.
 */
fun interface NodeExecutor {
    fun execute(frame: Frame, buffer: EffectBuffer): StepResult
}
```

#### 3.1. Буфер эффектов (`EffectBuffer` SoA)
```kotlin
package com.example.npc.pipeline.nodes.api.effect

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
    @JvmField internal val charBuffer: CharArray = CharArray(maxChars)

    @JvmField var effectCount: Int = 0
    @JvmField var argCount: Int = 0
    @JvmField var charCount: Int = 0

    fun mark(): Int = effectCount

    fun rollback(mark: Int) {
        if (mark < 0 || mark > effectCount) return
        val startArgToClear = argStarts[mark]
        for (i in startArgToClear until argCount) {
            refArgs[i] = null
        }
        argCount = startArgToClear
        effectCount = mark
        // charCount откатывается по сохраненному смещению
    }

    fun reset() {
        for (i in 0 until argCount) {
            refArgs[i] = null
        }
        effectCount = 0
        argCount = 0
        charCount = 0
        argStarts[0] = 0
    }
}
```

---

### 4. Контракт спецификации и каталога узлов (`NodeSpec`, `NodeRegistry`)

```kotlin
package com.example.npc.pipeline.nodes.api.spec

import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.frame.Bank

enum class NodeCategory { CONDITION, TRANSFORM, ACTION }

data class NodeTraits(
    val isPure: Boolean = true,
    val isDeterministic: Boolean = true,
    val costScore: Int = 1
)

interface NodeSpec {
    val id: String
    val category: NodeCategory
    val traits: NodeTraits
    val paramSchema: ParamSchema
    val portSchema: PortSchema

    /**
     * Вызывается компилятором во время фазы Lowering.
     * Связывает логические порты и параметры со статическими слотами FrameLayout.
     */
    fun bind(context: BindContext): NodeExecutor
}

interface BindContext {
    fun getParam(name: String): Any?
    fun getInputSlot(portName: String): Int
    fun getOutputSlot(portName: String): Int
    fun getBank(portName: String): Bank
    fun allocateScratchSlot(): Int
}

interface NodeRegistry {
    fun register(spec: NodeSpec)
    fun find(id: String): NodeSpec?
    fun getAll(): Collection<NodeSpec>
}
```
