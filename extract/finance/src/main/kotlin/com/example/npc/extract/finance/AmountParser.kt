package com.example.npc.extract.finance

object AmountParser {

    private const val MAX_RAW_LENGTH = 32
    private const val MAX_INTEGER_DIGITS = 15

    /**
     * Преобразует строковое представление суммы в неделимые минорные единицы (Long, копейки/центы).
     * Синоним parseMinor.
     */
    fun parseToMinor(rawAmount: String): Long? = parseMinor(rawAmount)

    /**
     * Разбирает строковое представление суммы в целочисленное количество минорных единиц (копеек/центов).
     * Работает без регулярных выражений за O(n) линейный проход.
     *
     * @param raw Строка с суммой (например: "5,47", "50.00", "1 250,50", "4.40", "500")
     * @param minorDigits Количество знаков дробной части для целевой валюты (для RUP/MDL/EUR/RUB/USD = 2)
     * @return Сумма в копейках/центах (Long) или null при невалидном формате
     */
    fun parseMinor(raw: String, minorDigits: Int = 2): Long? {
        if (raw.isEmpty() || raw.length > MAX_RAW_LENGTH) return null

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        var dotOrCommaCount = 0
        var digitCount = 0
        var lastSepIndex = -1

        for (i in 0 until trimmed.length) {
            val ch = trimmed[i]
            when {
                ch in '0'..'9' -> {
                    digitCount++
                }
                ch == '.' || ch == ',' -> {
                    dotOrCommaCount++
                    lastSepIndex = i
                }
                ch == ' ' || ch == '\u00A0' || ch == '\u202F' -> {
                    // Разрешенные разделители тысяч
                }
                else -> {
                    // Отрицательные числа, буквы и прочие недопустимые символы
                    return null
                }
            }
        }

        if (digitCount == 0 || dotOrCommaCount > 1) {
            return null
        }

        val hasSeparator = lastSepIndex != -1
        val integerPartEnd = if (hasSeparator) lastSepIndex else trimmed.length

        // Разбор целой части
        var integerMinor = 0L
        var intDigitCount = 0
        for (i in 0 until integerPartEnd) {
            val ch = trimmed[i]
            if (ch in '0'..'9') {
                intDigitCount++
                if (intDigitCount > MAX_INTEGER_DIGITS) return null
                try {
                    integerMinor = Math.addExact(Math.multiplyExact(integerMinor, 10L), (ch - '0').toLong())
                } catch (e: ArithmeticException) {
                    return null
                }
            }
        }

        if (intDigitCount == 0) return null

        // Разбор дробной части
        var fracMinor = 0L
        if (hasSeparator) {
            val fracDigits = StringBuilder(minorDigits)
            for (i in (lastSepIndex + 1) until trimmed.length) {
                val ch = trimmed[i]
                if (ch in '0'..'9') {
                    fracDigits.append(ch)
                } else if (ch != ' ' && ch != '\u00A0' && ch != '\u202F') {
                    return null
                }
            }
            if (fracDigits.isEmpty()) return null

            while (fracDigits.length < minorDigits) {
                fracDigits.append('0')
            }
            val normalizedFrac = fracDigits.substring(0, minorDigits)
            fracMinor = normalizedFrac.toLongOrNull() ?: return null
        }

        var factor = 1L
        repeat(minorDigits) {
            factor *= 10L
        }

        return try {
            Math.addExact(Math.multiplyExact(integerMinor, factor), fracMinor)
        } catch (e: ArithmeticException) {
            null
        }
    }
}
