package com.lirix.app.feature.lyrics

data class StudioLine(val id: Int, val text: String, val timeMs: Long?)

enum class StudioMode { QUICK, PRECISE }

/** Content history excludes navigation, transport ticks and viewport changes. */
data class KaraokeStudioState(
    val trackKey: String,
    val originalLines: List<StudioLine>,
    val lines: List<StudioLine>,
    val durationMs: Long,
    val selectedIndex: Int = 0,
    val cursorMs: Long = 0,
    val spanMs: Long = 15_000,
    val mode: StudioMode = StudioMode.QUICK,
    val undo: List<List<StudioLine>> = emptyList(),
    val redo: List<List<StudioLine>> = emptyList()
)

sealed interface KaraokeStudioEdit {
    data class SetTime(val index: Int, val timeMs: Long) : KaraokeStudioEdit
    data class Stamp(val index: Int, val rawPositionMs: Long, val reactionLagMs: Long) : KaraokeStudioEdit
    data class Nudge(val index: Int, val deltaMs: Long) : KaraokeStudioEdit
    data class ShiftTail(val index: Int, val deltaMs: Long) : KaraokeStudioEdit
    data class Clear(val index: Int) : KaraokeStudioEdit
    data class ReplaceText(val plainText: String) : KaraokeStudioEdit
    data object RestoreOriginal : KaraokeStudioEdit
    data object Undo : KaraokeStudioEdit
    data object Redo : KaraokeStudioEdit
}

sealed interface KaraokeStudioResult {
    data class Accepted(val state: KaraokeStudioState) : KaraokeStudioResult
    data class Rejected(val reason: String) : KaraokeStudioResult
}

object KaraokeStudioEngine {
    const val MAX_LINES = 5000
    private val tag = Regex("\\[(\\d+:\\d{2}(?:\\.\\d{1,3})?)\\]")
    private val metadata = Regex("^\\[(?:ar|ti|al|by|offset|re|ve|length):.*]$", RegexOption.IGNORE_CASE)

    fun createSession(trackKey: String, plainText: String, lrcText: String?, durationMs: Long): KaraokeStudioState {
        require(trackKey.isNotBlank()) { "Не выбрана песня" }
        val imported = mutableListOf<StudioLine>()
        if (!lrcText.isNullOrBlank()) {
            for (raw in lrcText.lineSequence()) {
                val source = raw.trim()
                if (source.isBlank() || metadata.matches(source)) continue
                val matches = tag.findAll(source).toList()
                var prefixEnd = 0
                val prefix = matches.takeWhile { match ->
                    val adjacent = match.range.first == prefixEnd
                    prefixEnd = match.range.last + 1
                    adjacent
                }
                if (prefix.isEmpty()) {
                    imported += StudioLine(imported.size, source, null)
                } else {
                    val text = source.substring(prefix.last().range.last + 1).trim()
                    if (text.isNotBlank()) prefix.forEach {
                        imported += StudioLine(imported.size, text, parseTimeInput(it.groupValues[1]))
                    }
                }
                require(imported.size <= MAX_LINES) { "В песне слишком много строк" }
            }
        }
        val lines = imported.ifEmpty {
            LrcSyncEngine.parsePlainToSyncLines(plainText).map { StudioLine(it.index, it.text, null) }
        }.toList()
        require(lines.size <= MAX_LINES) { "В песне слишком много строк" }
        return KaraokeStudioState(trackKey, lines, lines, durationMs.coerceAtLeast(0),
            selectedIndex = lines.indexOfFirst { it.timeMs == null }.takeIf { it >= 0 } ?: 0)
    }

    /** mm:ss, mm:ss.S, mm:ss.SS or mm:ss.SSS; no float rounding or overflow. */
    fun parseTimeInput(value: String): Long? {
        val match = Regex("^(\\d+):([0-5]\\d)(?:\\.(\\d{1,3}))?$").matchEntire(value.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3].padEnd(3, '0').toLong()
        if (minutes > (Long.MAX_VALUE - seconds * 1000 - fraction) / 60000) return null
        return minutes * 60000 + seconds * 1000 + fraction
    }

    fun formatTime(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0)
        return "${(safe / 60000).toString().padStart(2, '0')}:${(safe / 1000 % 60).toString().padStart(2, '0')}.${(safe % 1000).toString().padStart(3, '0')}"
    }

    fun validationError(state: KaraokeStudioState): String? {
        if (state.lines.isEmpty()) return "Добавьте текст песни"
        if (state.lines.any { it.timeMs == null }) return "Не все строки размечены"
        if (state.lines.any { !inBounds(it.timeMs!!, state.durationMs) }) return "Метка выходит за границы песни"
        if (conflicts(state.lines).isNotEmpty()) return "Время строк нарушает порядок текста"
        return null
    }

    fun exportLrc(state: KaraokeStudioState): String? {
        if (validationError(state) != null) return null
        return state.lines.joinToString("\n") { "${LrcSyncEngine.formatTimestamp(it.timeMs!!)} ${it.text}" }
    }

    fun reduce(state: KaraokeStudioState, edit: KaraokeStudioEdit): KaraokeStudioResult {
        fun reject(reason: String) = KaraokeStudioResult.Rejected(reason)
        if (edit == KaraokeStudioEdit.Undo) {
            val previous = state.undo.lastOrNull() ?: return KaraokeStudioResult.Accepted(state)
            return KaraokeStudioResult.Accepted(state.copy(lines = previous, undo = state.undo.dropLast(1),
                redo = (state.redo + listOf(state.lines)).takeLast(100), selectedIndex = state.selectedIndex.coerceIn(0, (previous.size - 1).coerceAtLeast(0))))
        }
        if (edit == KaraokeStudioEdit.Redo) {
            val next = state.redo.lastOrNull() ?: return KaraokeStudioResult.Accepted(state)
            return KaraokeStudioResult.Accepted(state.copy(lines = next, undo = (state.undo + listOf(state.lines)).takeLast(100),
                redo = state.redo.dropLast(1), selectedIndex = state.selectedIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))))
        }
        fun set(index: Int, time: Long?): List<StudioLine>? {
            if (index !in state.lines.indices || (time != null && !inBounds(time, state.durationMs))) return null
            return state.lines.mapIndexed { i, line -> if (i == index) line.copy(timeMs = time) else line }
        }
        val changed = when (edit) {
            is KaraokeStudioEdit.SetTime -> set(edit.index, edit.timeMs)
            is KaraokeStudioEdit.Clear -> set(edit.index, null)
            is KaraokeStudioEdit.Stamp -> {
                if (edit.rawPositionMs < 0 || edit.reactionLagMs < 0) return reject("Некорректная позиция воспроизведения")
                set(edit.index, (edit.rawPositionMs - edit.reactionLagMs).coerceAtLeast(0))
            }
            is KaraokeStudioEdit.Nudge -> {
                val old = state.lines.getOrNull(edit.index)?.timeMs ?: return reject("Сначала поставьте метку")
                val time = addExact(old, edit.deltaMs) ?: return reject("Слишком большой сдвиг")
                set(edit.index, time)
            }
            is KaraokeStudioEdit.ShiftTail -> {
                if (edit.index !in state.lines.indices) return reject("Выберите строку")
                if (state.lines.drop(edit.index).none { it.timeMs != null }) return reject("Во фрагменте нет меток")
                state.lines.mapIndexed { i, line ->
                    if (i < edit.index || line.timeMs == null) line else {
                        val time = addExact(line.timeMs, edit.deltaMs) ?: return reject("Слишком большой сдвиг")
                        if (!inBounds(time, state.durationMs)) return reject("Сдвиг выходит за границы песни")
                        line.copy(timeMs = time)
                    }
                }
            }
            is KaraokeStudioEdit.ReplaceText -> {
                val lines = LrcSyncEngine.parsePlainToSyncLines(edit.plainText)
                if (lines.isEmpty() || lines.size > MAX_LINES) return reject("Нужно от 1 до $MAX_LINES строк")
                // Never transfer old timestamps to newly inserted or reordered text by index.
                if (lines.map { it.text } == state.lines.map { it.text }) state.lines
                else lines.map { StudioLine(it.index, it.text, null) }
            }
            KaraokeStudioEdit.RestoreOriginal -> state.originalLines
            else -> return reject("Неизвестное действие")
        } ?: return reject("Метка выходит за границы песни или строка не найдена")
        // Imported bad ordering stays visible, and can be repaired one conflict at a time.
        if (edit != KaraokeStudioEdit.RestoreOriginal && edit !is KaraokeStudioEdit.ReplaceText &&
            (conflicts(changed) - conflicts(state.lines)).isNotEmpty()) return reject("Метка пересекает соседнюю строку")
        if (changed == state.lines) return KaraokeStudioResult.Accepted(state)
        return KaraokeStudioResult.Accepted(state.copy(lines = changed, undo = (state.undo + listOf(state.lines)).takeLast(100),
            redo = emptyList(), selectedIndex = state.selectedIndex.coerceIn(0, (changed.size - 1).coerceAtLeast(0))))
    }

    private fun inBounds(value: Long, duration: Long) = value >= 0 && (duration <= 0 || value <= duration)
    private fun addExact(value: Long, delta: Long): Long? {
        if (delta > 0 && value > Long.MAX_VALUE - delta) return null
        if (delta < 0 && value < Long.MIN_VALUE - delta) return null
        return value + delta
    }
    private fun conflicts(lines: List<StudioLine>): Set<Pair<Int, Int>> = lines.filter { it.timeMs != null }
        .zipWithNext().filter { (a, b) -> a.timeMs!! > b.timeMs!! }.map { (a, b) -> a.id to b.id }.toSet()
}
