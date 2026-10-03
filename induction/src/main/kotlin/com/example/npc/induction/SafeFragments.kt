package com.example.npc.induction

import com.example.npc.induction.model.SlotType

object SafeFragments {
    // Числовые суммы: до 4 триплетов тысяч, опциональная дробная часть из 1-2 знаков
    const val AMOUNT = """[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?"""

    // Поддерживаемые валюты региона
    const val CURRENCY = """MDL|RUP|USD|EUR|RUB|lei|лей|леев|руб\.?|р\.|\$|€"""

    // 4 цифры маски карты
    const val CARD4 = """[0-9]{4}"""

    // Наименование мерчанта: ленивый квантификатор от 2 до 64 символов без переноса строки
    const val MERCHANT = """[^\n]{2,64}?"""

    // Дата: DD.MM.YYYY или DD/MM/YY
    const val DATE = """[0-9]{2}[./\-][0-9]{2}(?:[./\-][0-9]{2,4})?"""

    // Время: HH:MM или HH:MM:SS
    const val TIME = """[0-9]{2}:[0-9]{2}(?::[0-9]{2})?"""

    // Пробелы
    const val WS = """\s+"""
    const val WS_OPT = """\s*"""

    // Ограничитель конца строки или сообщения
    const val LINE_END = """(?:\n|$)"""

    // Специализированные компактные слотовые регулярки для конвейера (OPT-PIPE-001)
    const val DEFAULT_ANCHOR_MAX_LENGTH = 60
    const val DEFAULT_SLOT_MAX_LENGTH = 70

    // AmountSlotExtractor: (?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?
    const val SLOT_AMOUNT_PATTERN = """(?i)(?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?"""

    // CardMaskSlotExtractor: (?:карте|карту)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)
    const val SLOT_CARD_MASK_PATTERN = """(?i)(?:карте|карту|card)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)"""

    // MerchantSlotExtractor: (?:от|в|списано)\s+(?P<merchant>[A-ZА-ЯЁ][a-zа-яё]+(?:\s+[A-ZА-ЯЁ]\.)?)
    const val SLOT_MERCHANT_PATTERN = """(?i)(?:от|в|списано)\s+(?P<merchant>[A-ZА-ЯЁ][a-zа-яё]+(?:\s+[A-ZА-ЯЁ]\.)?)"""

    // BalanceSlotExtractor: (?:Остаток|Баланс):\s*(?P<bal>\d+(?:[.,]\d{2})?)
    const val SLOT_BALANCE_PATTERN = """(?i)(?:Остаток|Баланс|Sold):\s*(?P<bal>\d+(?:[.,]\d{2})?)"""

    // Точные варианты без флага (?i) согласно спецификации OPT-PIPE-001
    const val SPEC_SLOT_AMOUNT_PATTERN = """(?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?"""
    const val SPEC_SLOT_CARD_MASK_PATTERN = """(?:карте|карту)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)"""
    const val SPEC_SLOT_MERCHANT_PATTERN = """(?:от|в|списано)\s+(?P<merchant>[A-ZА-ЯЁ][a-zа-яё]+(?:\s+[A-ZА-ЯЁ]\.?)?)"""
    const val SPEC_SLOT_BALANCE_PATTERN = """(?:Остаток|Баланс):\s*(?P<bal>\d+(?:[.,]\d{2})?)"""

    fun fragmentForRole(role: SlotType): String = when (role) {
        SlotType.TX_AMOUNT, SlotType.AMOUNT, SlotType.BALANCE, SlotType.FEE, SlotType.OTHER -> AMOUNT
        SlotType.CURRENCY, SlotType.BALANCE_CURRENCY -> CURRENCY
        SlotType.CARD_MASK -> CARD4
        SlotType.MERCHANT -> MERCHANT
    }

    fun defaultSlotPatternFor(role: SlotType): String = when (role) {
        SlotType.TX_AMOUNT, SlotType.AMOUNT -> SLOT_AMOUNT_PATTERN
        SlotType.CARD_MASK -> SLOT_CARD_MASK_PATTERN
        SlotType.MERCHANT -> SLOT_MERCHANT_PATTERN
        SlotType.BALANCE -> SLOT_BALANCE_PATTERN
        SlotType.CURRENCY -> """(?i)(?P<curr>$CURRENCY)"""
        SlotType.BALANCE_CURRENCY -> """(?i)(?P<balcurr>$CURRENCY)"""
        SlotType.FEE -> """(?i)(?:комиссия:\s*)?(?P<fee>$AMOUNT)"""
        SlotType.OTHER -> """(?i)(?P<other>[^\n]{1,64}?)"""
    }

    fun quoteLiteral(literal: String): String {
        return java.util.regex.Pattern.quote(literal)
    }
}
