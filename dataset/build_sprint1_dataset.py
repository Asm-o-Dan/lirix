#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Builder for Sprint 1 Golden Dataset:
Expands dataset/golden_dataset.json to 105+ high-quality annotated samples per category (>= 1260 samples).
Integrates real anonymized Poco M7 device logs and extensive Hard Cases with clear disambiguation reasons.
"""

import json
import os
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

TARGET_CATEGORIES = [
    "finance.income",
    "finance.expense.food",
    "finance.expense.transport",
    "finance.expense.shopping",
    "finance.transfer",
    "music",
    "study.lesson",
    "study.deadline",
    "message",
    "advertisement",
    "authentication",
    "other"
]

def generate_all_samples():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    current_golden = os.path.join(base_dir, "golden_dataset.json")
    
    with open(current_golden, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    existing_samples = data.get("samples", [])
    print(f"Loaded {len(existing_samples)} existing samples from golden_dataset.json")

    # Map existing by category
    by_cat = {c: [] for c in TARGET_CATEGORIES}
    for s in existing_samples:
        if s["category"] in by_cat:
            by_cat[s["category"]].append(s)

    # 1. Real device logs from Poco M7
    poco_samples = [
        {
            "id": "poco_ym_001",
            "package_name": "ru.yandex.music",
            "source_type": "notification",
            "title": "🄴  2007",
            "text": "Йорш, Дима Сокол",
            "sub_text": None,
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "Йорш, Дима Сокол", "track": "2007", "playback_state": "PLAYING"},
            "is_hard_case": True,
            "hard_case_reason": "Unicode Explicit tag [E] в начале названия трека; перехвачено через NotificationListener",
            "expected_precision_critical": False
        },
        {
            "id": "poco_ym_002",
            "package_name": "ru.yandex.music",
            "source_type": "notification",
            "title": "🄴  виски",
            "text": "МОЛОДОСТЬ ВНУТРИ",
            "sub_text": None,
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "МОЛОДОСТЬ ВНУТРИ", "track": "виски", "playback_state": "PLAYING"},
            "is_hard_case": True,
            "hard_case_reason": "Unicode Explicit tag [E] в названии трека; кириллический трек",
            "expected_precision_critical": False
        },
        {
            "id": "poco_ym_003",
            "package_name": "ru.yandex.music",
            "source_type": "notification",
            "title": "🄴  If You Say So",
            "text": "Lea Michele",
            "sub_text": None,
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "Lea Michele", "track": "If You Say So", "playback_state": "PLAYING"},
            "is_hard_case": True,
            "hard_case_reason": "Unicode Explicit tag [E], латинские метаданные трека",
            "expected_precision_critical": False
        },
        {
            "id": "poco_ym_004",
            "package_name": "ru.yandex.music",
            "source_type": "notification",
            "title": "Пляска Дурака",
            "text": "Алиса",
            "sub_text": None,
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "Алиса", "track": "Пляска Дурака", "playback_state": "PLAYING"},
            "is_hard_case": False,
            "hard_case_reason": None,
            "expected_precision_critical": False
        },
        {
            "id": "poco_ayugram_001",
            "package_name": "com.radolyn.ayugram",
            "source_type": "notification",
            "title": "Мама",
            "text": "Поделиться ссылкой https://makler.md/ru/odds-and-ends-exchange-and-buy/food-beverage-and-packaging/packaging/an/25782",
            "sub_text": "63 новых сообщения из 8 чатов",
            "category": "message",
            "intent": "personal_message",
            "entities": {"sender": "Мама"},
            "is_hard_case": True,
            "hard_case_reason": "Кастомный Telegram-клиент AyuGram (пакет com.radolyn.ayugram без слова 'telegram'); содержит URL и агрегированный счетчик",
            "expected_precision_critical": True
        },
        {
            "id": "poco_sber_001",
            "package_name": "ru.sberbankmobile",
            "source_type": "notification",
            "title": "Сбербанк Онлайн",
            "text": "💸 Деньги списаны. 1 450 ₽ в пользу Супермаркет. Баланс: 12 300 ₽",
            "sub_text": None,
            "category": "finance.expense.food",
            "intent": "groceries_payment",
            "entities": {"amount": 1450.0, "currency": "RUB", "merchant": "Супермаркет"},
            "is_hard_case": True,
            "hard_case_reason": "Эмодзи '💸 Деньги списаны' вместо стандартного ключевого слова 'Оплата'",
            "expected_precision_critical": True
        },
        {
            "id": "poco_apb_001",
            "package_name": "com.apb.mobile",
            "source_type": "notification",
            "title": "Агропромбанк",
            "text": "Списание 85.00 PRB. Оплата услуг: Связь IDC. Доступно: 412.50 PRB",
            "sub_text": None,
            "category": "finance.transfer",
            "intent": "utility_payment",
            "entities": {"amount": 85.0, "currency": "PRB", "merchant": "Связь IDC"},
            "is_hard_case": True,
            "hard_case_reason": "Региональный банк ПМР (com.apb.mobile), национальная валюта PRB (приднестровский рубль)",
            "expected_precision_critical": True
        },
        {
            "id": "poco_apb_002",
            "package_name": "com.apb.mobile",
            "source_type": "notification",
            "title": "АПБ",
            "text": "Зачисление перевода APB Online: +250.00 PRB от Шестаков В.И.",
            "sub_text": None,
            "category": "finance.income",
            "intent": "transfer_received",
            "entities": {"amount": 250.0, "currency": "PRB", "sender": "Шестаков В.И."},
            "is_hard_case": True,
            "hard_case_reason": "Краткий заголовок 'АПБ', зачисление перевода в валюте PRB",
            "expected_precision_critical": True
        },
        {
            "id": "poco_revanced_001",
            "package_name": "app.revanced.android.youtube",
            "source_type": "notification",
            "title": "YouTube ReVanced",
            "text": "System of a Down — Chop Suey! (Official Video)",
            "sub_text": "Воспроизведение",
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "System of a Down", "track": "Chop Suey!", "playback_state": "PLAYING"},
            "is_hard_case": True,
            "hard_case_reason": "YouTube ReVanced (app.revanced.android.youtube) форк, фоновое воспроизведение",
            "expected_precision_critical": False
        },
        {
            "id": "poco_revanced_002",
            "package_name": "app.revanced.android.youtube",
            "source_type": "mediasession",
            "title": "Hans Zimmer - Time (Inception)",
            "text": "Hans Zimmer — Inception Soundtrack",
            "sub_text": None,
            "category": "music",
            "intent": "media_playback",
            "entities": {"artist": "Hans Zimmer", "track": "Time", "playback_state": "PLAYING"},
            "is_hard_case": True,
            "hard_case_reason": "MediaSession из ReVanced, трек содержит дефис и скобки",
            "expected_precision_critical": False
        },
        {
            "id": "poco_sms_001",
            "package_name": "com.google.android.apps.messaging",
            "source_type": "sms",
            "title": "1611",
            "text": "66486 – код к сессии 11629924. Никому не сообщайте",
            "sub_text": None,
            "category": "authentication",
            "intent": "otp_verification",
            "entities": {"auth_code": "66486"},
            "is_hard_case": True,
            "hard_case_reason": "Короткий номер 1611, обратный формат OTP (цифры кода перед словом 'код к сессии')",
            "expected_precision_critical": True
        },
        {
            "id": "poco_prisbank_001",
            "package_name": "com.prisbank.app",
            "source_type": "notification",
            "title": "💸 Деньги списаны",
            "text": "6.00 RUP",
            "sub_text": None,
            "category": "finance.expense.shopping",
            "intent": "card_payment",
            "entities": {"amount": 6.0, "currency": "RUP"},
            "is_hard_case": True,
            "hard_case_reason": "Банковский пуш '💸 Деньги списаны' в валюте RUP (рубли ПМР) без явного мерчанта",
            "expected_precision_critical": True
        }
    ]

    # Filter out any s1_ generated duplicates to cleanly regenerate up to target_count
    for cat in TARGET_CATEGORIES:
        by_cat[cat] = [s for s in by_cat[cat] if not s["id"].startswith("s1_") and not s["id"].startswith("poco_")]

    for ps in poco_samples:
        by_cat[ps["category"]].append(ps)

    # 2. Generator routines to reach 105+ samples per category
    # Each category gets diverse banks, senders, formats, edge cases
    
    # --- FINANCE.INCOME ---
    income_templates = [
        ("ru.sberbankmobile", "СберБанк", "Зачисление зарплаты {amt} ₽ от ООО '{comp}'. Баланс: {bal} ₽", "salary_credited", {"sender": "{comp}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Входящий перевод СБП +{amt} ₽ от {name}. Сообщение: {msg}", "transfer_received", {"sender": "{name}", "channel": "СБП"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Возврат покупки {amt} ₽ от магазина '{shop}'. Деньги поступили на счет", "refund_received", {"merchant": "{shop}"}, True, "Возврат средств за отмену покупки, классифицируется в finance.income"),
        ("ru.vtb24.mobilebanking.android", "ВТБ", "Зачисление стипендии {amt} ₽. Карта МИР •• {card}. Баланс: {bal} ₽", "stipend_credited", {}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Начислен кэшбэк +{amt} ₽ по программе лояльности. Доступно: {bal} ₽", "cashback_credited", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Проценты по вкладу 'Лучший %': +{amt} ₽ зачислены на счет •• {card}", "interest_credited", {}, False, None),
        ("com.ftc.faktura.multibank", "Агропромбанк", "Пополнение счета на {amt} PRB через кассу отделения {branch}", "cash_deposit", {}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Авито Доставка: выплата за проданный товар +{amt} ₽ на карту •• {card}", "marketplace_payout", {"merchant": "Авито"}, False, None),
        ("ru.raiffeisennews", "Райффайзен", "Входящий перевод +{amt} ₽ от {name} по номеру счета", "transfer_received", {"sender": "{name}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Выплата дивидендов по акциям {comp}: +{amt} ₽ на брокерский счет", "dividends_credited", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Компенсация по ОСАГО: зачислено {amt} ₽ от страховой компании", "insurance_payout", {}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Пополнение карты через СБП: +{amt} ₽ из Банка ВТБ", "sbp_incoming", {"channel": "СБП"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Возврат налога (вычет за обучение): +{amt} ₽ от Федерального казначейства", "tax_refund", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Пополнение счета наличными в банкомате {atm}: +{amt} ₽", "atm_deposit", {}, False, None),
        ("com.ftc.faktura.multibank", "Эксимбанк", "Зачисление денежного перевода Золотая Корона: +{amt} USD", "wire_income", {}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Бонус за рекомендацию друга: +{amt} ₽ зачислены на карту", "referral_bonus", {}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Чек коррекции: возвращено {amt} ₽ после перерасчета за отопление", "utility_refund", {}, True, "Служебный возврат средств за коммунальные услуги"),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Пополнение копилки: +{amt} ₽ (автоматическое округление покупок)", "piggybank_deposit", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Возврат {amt}", "refund_received", {}, True, "Сверхкраткий текст возврата средств (Hard Case: 'Возврат 1000')"),
        ("ru.sberbankmobile", "Сбер", "Пополнение {amt}", "account_topup", {}, True, "Сверхкраткий текст пополнения счета (Hard Case: 'Сбер: Пополнение 1000')"),
    ]

    # --- FINANCE.EXPENSE.FOOD ---
    food_templates = [
        ("ru.sberbankmobile", "Сбер", "Оплата {amt} ₽, Пятёрочка. Карта •• {card}. Баланс: {bal} ₽", "groceries_payment", {"merchant": "Пятёрочка"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, ВкусВилл. Начислен кэшбэк {cb} ₽", "groceries_payment", {"merchant": "ВкусВилл"}, False, None),
        ("ru.yandex.taxi", "Яндекс Еда", "Оплата заказа {amt} ₽ в ресторане '{rest}'. Курьер выехал", "food_delivery", {"merchant": "Яндекс Еда"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Списание {amt} ₽, Додо Пицца. Приятного аппетита!", "restaurant_payment", {"merchant": "Додо Пицца"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Перекрёсток. Супермаркеты", "groceries_payment", {"merchant": "Перекрёсток"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Оплата {amt} ₽, Магнит у дома. Доступно: {bal} ₽", "groceries_payment", {"merchant": "Магнит"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Списание {amt} ₽, SAMOKAT. Заказ доставлен за 15 минут", "groceries_delivery", {"merchant": "Самокат"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Бургер Кинг. Фастфуд", "fastfood_payment", {"merchant": "Бургер Кинг"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Оплата {amt} ₽, Cofix. Кофе с собой", "cafe_payment", {"merchant": "Cofix"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Ресторан '{rest}': оплата счета {amt} ₽. Карта •• {card}", "restaurant_payment", {"merchant": "{rest}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, КуулКлевер (Мясновъ / Отдохни). Баланс: {bal} ₽", "groceries_payment", {"merchant": "КуулКлевер"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Оплата {amt} ₽, Столовая №1. Баланс: {bal} ₽", "canteen_payment", {"merchant": "Столовая №1"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Лента Гипермаркет. Продукты питания", "groceries_payment", {"merchant": "Лента"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Оплата {amt} ₽, Rostic's. Чек №{chk}", "fastfood_payment", {"merchant": "Rostic's"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Списание {amt} ₽, Delivery Club (Маркет Деливери)", "food_delivery", {"merchant": "Маркет Деливери"}, False, None),
        ("com.ftc.faktura.multibank", "Агропромбанк", "Оплата покупки: {amt} PRB, Супермаркет 'Шериф-14'. Баланс: {bal} PRB", "groceries_payment", {"merchant": "Шериф"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Азбука Вкуса. Премиум продукты", "groceries_payment", {"merchant": "Азбука Вкуса"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Оплата {amt} ₽, Пекарня 'Хлебница'. Выпечка и кофе", "bakery_payment", {"merchant": "Хлебница"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Списание {amt} ₽, Суши Wok. Заказ на вынос", "takeaway_payment", {"merchant": "Суши Wok"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Оплата 1000", "generic_payment", {}, True, "Сверхкраткий текст оплаты без указания категории и мерчанта (Hard Case: 'Оплата 1000')"),
    ]

    # --- FINANCE.EXPENSE.TRANSPORT ---
    transport_templates = [
        ("ru.yandex.taxi", "Яндекс Go", "Поездка завершена. Списано {amt} ₽ за поездку в тарифе Комфорт", "taxi_payment", {"merchant": "Яндекс Go"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Оплата проезда: Метрополитен {amt} ₽. Карта •• {card}", "transit_payment", {"merchant": "Московский метрополитен"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, ЛУКОЙЛ АЗС №{num}. Топливо АИ-95", "gas_payment", {"merchant": "ЛУКОЙЛ"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Оплата {amt} ₽, Каршеринг Делимобиль. Аренда 34 мин", "carsharing_payment", {"merchant": "Делимобиль"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Списание {amt} ₽, АЗС Газпромнефть. Топливо ОПТИ-95", "gas_payment", {"merchant": "Газпромнефть"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата парковки {amt} ₽, Парковки Москвы. Зона {num}", "parking_payment", {"merchant": "Парковки Москвы"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Покупка билета: РЖД {amt} ₽. Поезд {num}, вагон 07", "train_ticket", {"merchant": "РЖД"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Списание {amt} ₽, Аэроэкспресс. Билет Стандарт", "train_ticket", {"merchant": "Аэроэкспресс"}, False, None),
        ("com.citydrive.app", "Ситидрайв", "Поездка завершена. С карты •• {card} списано {amt} ₽", "carsharing_payment", {"merchant": "Ситидрайв"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Авиакомпания S7 Airlines. Билет Москва - Сочи", "flight_ticket", {"merchant": "S7 Airlines"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Пополнение карты Тройка на {amt} ₽ через приложение Метро", "transit_topup", {"merchant": "Тройка"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, АЗС Роснефть. Дизельное топливо", "gas_payment", {"merchant": "Роснефть"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Списание {amt} ₽, Каршеринг BelkaCar. Тариф поминутный", "carsharing_payment", {"merchant": "BelkaCar"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Оплата проезда: Мосгортранс {amt} ₽. Автобус м3", "bus_payment", {"merchant": "Мосгортранс"}, False, None),
        ("com.ftc.faktura.multibank", "Агропромбанк", "Оплата топлива: {amt} PRB, АЗС Шериф №{num}", "gas_payment", {"merchant": "АЗС Шериф"}, False, None),
        ("ru.yandex.taxi", "Яндекс Драйв", "Аренда завершена. Списано {amt} ₽ за поездку", "carsharing_payment", {"merchant": "Яндекс Драйв"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Платная дорога М-11 'Нева'. Транспондер", "toll_road_payment", {"merchant": "Автодор"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Покупка билетов: Авиабилеты Победа {amt} ₽. Рейс DP-{num}", "flight_ticket", {"merchant": "Победа"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Списание {amt} ₽, Автомойка 'МойСам'. Самообслуживание", "carwash_payment", {"merchant": "Автомойка"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Whoosh Кикшеринг. Аренда электросамоката", "scooter_payment", {"merchant": "Whoosh"}, False, None),
    ]

    # --- FINANCE.EXPENSE.SHOPPING ---
    shopping_templates = [
        ("com.wildberries.wbdeti", "Wildberries", "Заказ №{num} оплачен: списано {amt} ₽ с карты •• {card}", "marketplace_payment", {"merchant": "Wildberries"}, False, None),
        ("ru.ozon.app.android", "OZON", "Списание {amt} ₽ за оформленный заказ. Доставка в пункт выдачи", "marketplace_payment", {"merchant": "OZON"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Покупка {amt} ₽, Аптека Апрель. Лекарства и витамины", "pharmacy_payment", {"merchant": "Аптека Апрель"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, DNS Электроника. Чек №{num}", "electronics_payment", {"merchant": "DNS"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Покупка {amt} ₽, Золотое Яблоко. Косметика и парфюмерия", "cosmetics_payment", {"merchant": "Золотое Яблоко"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Списание {amt} ₽, Леруа Мерлен (Лемана ПРО). Товары для дома", "home_goods_payment", {"merchant": "Лемана ПРО"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Спортмастер. Одежда и спортинвентарь", "sports_payment", {"merchant": "Спортмастер"}, False, None),
        ("ru.yandex.market", "Яндекс Маркет", "Оплата заказа на сумму {amt} ₽ подтверждена. Доставка курьером", "marketplace_payment", {"merchant": "Яндекс Маркет"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Покупка {amt} ₽, М.Видео. Бытовая техника", "electronics_payment", {"merchant": "М.Видео"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Списание {amt} ₽, Аптека Ригла. Чек №{num}", "pharmacy_payment", {"merchant": "Аптека Ригла"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Fix Price. Товары для дома", "retail_payment", {"merchant": "Fix Price"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Покупка {amt} ₽, Читай-Город. Учебная литература", "books_payment", {"merchant": "Читай-Город"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Оплата {amt} ₽, Lamoda. Одежда и обувь с примеркой", "clothing_payment", {"merchant": "Lamoda"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Покупка {amt} ₽, ВсеИнструменты.ру. Инструменты и крепеж", "hardware_payment", {"merchant": "ВсеИнструменты"}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Списание {amt} ₽, Детский Мир. Игрушки и товары для школы", "retail_payment", {"merchant": "Детский Мир"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Hoff Мебель. Товары для интерьера", "furniture_payment", {"merchant": "Hoff"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Оплата {amt} ₽, Аптека Горздрав. Медикаменты", "pharmacy_payment", {"merchant": "Горздрав"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Покупка {amt} ₽, Четыре Лапы. Зоотовары и корм", "pet_supplies_payment", {"merchant": "Четыре Лапы"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Списание {amt} ₽, Ситилинк. Комплектующие для ПК", "electronics_payment", {"merchant": "Ситилинк"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Списание 0 ₽, Проверка карты для привязки подписки 'Иви'", "card_auth", {}, True, "Нулевое списание (холд/верификация карты) без фактического расхода"),
    ]

    # --- FINANCE.TRANSFER ---
    transfer_templates = [
        ("ru.sberbankmobile", "Сбер", "Перевод {amt} ₽ клиенту Сбера {name}. Баланс: {bal} ₽", "p2p_transfer", {"recipient": "{name}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Исходящий перевод СБП {amt} ₽ в Банк ВТБ ({name}). Без комиссии", "sbp_outgoing", {"recipient": "{name}", "channel": "СБП"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Перевод {amt} ₽ между своими счетами: с Текущего на Накопительный", "internal_transfer", {}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Перевод по номеру телефона: {amt} ₽ отправлены в Т-Банк. Комиссия 0 ₽", "phone_transfer", {}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Перевод {amt} ₽ на карту стороннего банка •• {card}. Баланс: {bal} ₽", "card2card_transfer", {}, False, None),
        ("ru.vtb24.mobilebanking.android", "ВТБ", "Исполнен перевод {amt} ₽ по банковским реквизитам. Счет •• {card}", "wire_transfer", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Автоплатеж: {amt} ₽ переведено в пользу оператора связи МТС", "telecom_autopay", {"merchant": "МТС"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Пополнение брокерского счета: {amt} ₽ переведено с карты Black", "brokerage_transfer", {}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Перевод {amt} ₽ в пользу ЖКУ г. Москвы. Лицевой счет {num}", "utility_transfer", {"merchant": "ЖКУ"}, False, None),
        ("com.ftc.faktura.multibank", "Агропромбанк", "Перевод через APB Online: {amt} PRB получателю {name}", "p2p_transfer", {"recipient": "{name}"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Списание комиссии {amt} ₽ за годовое обслуживание банковской карты", "bank_fee", {}, True, "Комиссионное списание банка (financial debit), не покупка"),
        ("com.idamob.tinkoff.android", "Т-Банк", "Перевод {amt} ₽ на Инвесткопилку. Округление трат", "invest_piggybank", {}, False, None),
        ("ru.sberbankmobile", "СберБанк", "Запрос на перевод {amt} ₽ от пользователя {name} исполнен", "p2p_transfer", {"recipient": "{name}"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Перевод {amt} ₽ по реквизитам договора аренды помещения", "rent_transfer", {}, False, None),
        ("ru.sberbankmobile", "Сбер", "Оплата кредита: {amt} ₽ списано в счет погашения очередного платежа", "loan_repayment", {}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Перевод {amt} ₽ на карту Сбербанка через Систему быстрых платежей", "sbp_outgoing", {"channel": "СБП"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Перевод {amt}", "p2p_transfer", {}, True, "Сверхкраткий текст перевода без контекста (Hard Case: 'Сбер: Перевод 1000')"),
        ("ru.sberbankmobile", "Сбер", "Списание {amt}", "general_debit", {}, True, "Сверхкраткий текст списания без мерчанта (Hard Case: 'Списание 1000')"),
        ("com.idamob.tinkoff.android", "Т-Банк", "Напоминание о предстоящем платеже по кредитке {amt} ₽ до 28 числа", "payment_reminder", {}, True, "Напоминание о платеже, а не списание денег"),
        ("ru.sberbankmobile", "Сбер", "Отклонено: недостаточно средств для перевода {amt} ₽ клиенту {name}", "transfer_failed", {}, True, "Неудавшийся перевод из-за нехватки баланса, факт передачи денег не состоялся"),
    ]

    # --- MUSIC ---
    music_templates = [
        ("com.spotify.music", "Spotify", "{artist} — {track}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("ru.yandex.music", "Яндекс Музыка", "{track} • {artist}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.google.android.apps.youtube.music", "YouTube Music", "{artist} - {track} (Live)", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("org.videolan.vlc", "VLC", "Воспроизведение: {track} [{artist}]", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.vkontakte.android", "VK Музыка", "{track} — {artist}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.apple.android.music", "Apple Music", "Сейчас играет: {track} исполнителя {artist}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.soundcloud.android", "SoundCloud", "{artist} - {track} [Remix 2026]", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.maxmpz.audioplayer", "Poweramp", "{artist} / {track} (Lossless FLAC)", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("app.revanced.android.youtube", "YouTube ReVanced", "{artist} — {track} (Audio Track)", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.spotify.music", "Spotify", "Пауза: {track} — {artist}", "media_paused", {"artist": "{artist}", "track": "{track}", "playback_state": "PAUSED"}, False, None),
        ("ru.yandex.music", "Яндекс Музыка", "Моя волна: {track} исполнителя {artist}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.google.android.apps.youtube.music", "YouTube Music", "{artist} • {track} [Official Audio]", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.deezer.android.app", "Deezer", "Flow: {track} by {artist}", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("org.videolan.vlc", "VLC", "{track}.mp3 [Аудиоплеер]", "media_playback", {"track": "{track}", "playback_state": "PLAYING"}, True, "Локальный файл воспроизведения с расширением .mp3 без тегов исполнителя"),
        ("com.spotify.music", "Spotify", "Подкаст 'Запуск завтра': Эпизод №{num}", "podcast_playback", {"track": "Запуск завтра", "playback_state": "PLAYING"}, True, "Медиа-сессия подкаста без артиста (только название шоу)"),
        ("ru.yandex.music", "Яндекс Музыка", "Аудиокнига: 'Мастер и Маргарита' (Глава {num})", "audiobook_playback", {"track": "Мастер и Маргарита", "playback_state": "PLAYING"}, True, "Воспроизведение аудиокниги в музыкальном приложении"),
        ("com.spotify.music", "Spotify", "Spotify Connect: воспроизведение на Smart TV Samsung", "remote_playback", {}, True, "Уведомление о трансляции воспроизведения на внешнее устройство"),
        ("app.revanced.android.youtube", "ReVanced Music", "{artist} - {track} (Remastered 4K)", "media_playback", {"artist": "{artist}", "track": "{track}", "playback_state": "PLAYING"}, False, None),
        ("com.vkontakte.android", "VK Музыка", "Музыка на паузе: {track}", "media_paused", {"track": "{track}", "playback_state": "PAUSED"}, False, None),
        ("com.google.android.apps.youtube.music", "YouTube Music", "Загрузка трека {track} в кэш для офлайн-прослушивания", "media_download", {"track": "{track}"}, True, "Служебное уведомление о загрузке музыки, а не само воспроизведение"),
    ]

    # --- STUDY.LESSON ---
    lesson_templates = [
        ("org.telegram.messenger", "Староста группы", "Завтра пара по {subj} в {time}, аудитория {aud}", "lesson_schedule", {"subject": "{subj}"}, False, None),
        ("com.google.android.calendar", "Google Календарь", "Через 15 минут: Лекция по дисциплине '{subj}', ауд. {aud}", "lesson_reminder", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Учебный чат", "Внимание: Завтра пара отменяется! Преподаватель заболел", "lesson_cancelled", {}, True, "Отмена учебного занятия (Hard Case: 'Завтра пара отменяется')"),
        ("org.telegram.messenger", "Деканат", "Завтра замена пары: вместо {subj} будет {subj2} в ауд. {aud}", "lesson_rescheduled", {"subject": "{subj2}"}, True, "Замена предмета в расписании (Hard Case: 'Завтра замена пары')"),
        ("org.telegram.messenger", "Староста", "Завтра пара", "lesson_short", {}, True, "Сверхкраткое разговорное сообщение о занятии (Hard Case: 'Завтра пара')"),
        ("com.moodle.moodlemobile", "Moodle SPSU", "Ссылка на онлайн-семинар по {subj} в Zoom: https://zoom.us/j/{num}", "online_lesson_link", {"subject": "{subj}"}, False, None),
        ("com.google.android.apps.tachyon", "Google Meet", "Занятие '{subj}' началось. Нажмите для подключения к видеозвонку", "online_lesson_started", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Преподаватель {name}", "Семинар переносится на субботу в 11:30 в дистанционном формате", "lesson_rescheduled", {}, False, None),
        ("com.spsu.schedule", "Расписание ПГУ", "Расписание на завтра: 1. {subj} (ауд. {aud}), 2. {subj2} (лаб)", "daily_schedule", {}, False, None),
        ("org.telegram.messenger", "Староста группы", "Ребята, первой пары нет! Приходим ко 2-й к 10:15", "lesson_cancelled", {}, False, None),
        ("com.moodle.moodlemobile", "ЭОС ПГУ", "Опубликовано расписание консультаций перед экзаменом по курсу '{subj}'", "exam_consultation", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Учебная часть", "Внимание студентов: лекция профессора {name} состоится в актовом зале", "lecture_location_change", {}, False, None),
        ("com.microsoft.teams", "Microsoft Teams", "Собрание 'Лабораторный практикум по {subj}' началось", "online_lesson_started", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Чат старост", "Завтра сокращенный день: пары по 40 минут по расписанию среды", "shortened_schedule", {}, False, None),
        ("com.google.android.calendar", "Календарь", "Экзамен: {subj}. Начало в 09:00, аудитория {aud}", "exam_event", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Группа ПИ-22", "Кто знает, в какой аудитории сегодня пересдача по {subj}?", "peer_question", {"subject": "{subj}"}, True, "Вопрос одногруппника о расписании, классифицируется как учебный контекст"),
        ("com.moodle.moodlemobile", "Moodle", "Преподаватель открыл доступ к материалам лекции №{num} по {subj}", "materials_opened", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Староста", "Завтра ко второй паре, физкультура на стадионе отменена из-за дождя", "pe_lesson_cancelled", {}, False, None),
        ("com.spsu.schedule", "Электронный журнал", "У вас изменилась аудитория для пары '{subj}': новая ауд. {aud}", "room_change", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Дежурный по факультету", "Напоминание: завтра в 14:00 собрание курса по поводу производственной практики", "internship_meeting", {}, False, None),
    ]

    # --- STUDY.DEADLINE ---
    deadline_templates = [
        ("com.moodle.moodlemobile", "Moodle SPSU", "Дедлайн задания 'Лабораторная работа №{num}' по {subj} истекает сегодня в 23:59", "assignment_deadline", {"subject": "{subj}", "deadline": "сегодня в 23:59"}, False, None),
        ("org.telegram.messenger", "Староста", "Не забудь сдать лабу", "assignment_reminder", {"subject": "Лабораторная работа"}, True, "Разговорное напоминание о лабораторной работе (Hard Case: 'Не забудь лабу')"),
        ("com.moodle.moodlemobile", "ЭОС ПГУ", "Тест по модулю '{subj}' будет закрыт завтра в 20:00. Осталась 1 попытка", "test_deadline", {"subject": "{subj}", "deadline": "завтра в 20:00"}, False, None),
        ("org.telegram.messenger", "Учебный чат", "Народ, крайний срок отправки отчета по практике — эта пятница до 18:00!", "report_deadline", {"subject": "Отчет по практике", "deadline": "пятница 18:00"}, False, None),
        ("com.github.android", "GitHub Classroom", "Assignment '{subj}-lab{num}': submission deadline in 6 hours. Push your commits.", "vcs_deadline", {"subject": "{subj}", "deadline": "in 6 hours"}, False, None),
        ("com.moodle.moodlemobile", "Moodle", "Срок сдачи курсового проекта по {subj} продлен до 15 ноября 23:59", "deadline_extended", {"subject": "{subj}", "deadline": "15 ноября 23:59"}, False, None),
        ("org.telegram.messenger", "Куратор курса", "Студенты должники! Закрытие академических задолженностей строго до конца недели", "debt_deadline", {}, False, None),
        ("com.google.android.apps.tasks", "Google Задачи", "Срок выполнения: Отправить презентацию к диплому научному руководителю", "task_deadline", {}, False, None),
        ("com.moodle.moodlemobile", "Moodle SPSU", "Задание 'Домашнее задание №{num}' ожидает проверки. Прием ответов завершен", "assignment_closed", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Староста", "Дедлайн по выбору темы курсовой работы закрывается сегодня в 17:00", "topic_deadline", {"deadline": "сегодня в 17:00"}, False, None),
        ("com.moodle.moodlemobile", "ЭОС ПГУ", "Напоминание: Срок выполнения контрольной работы по {subj} истекает через 2 дня", "test_deadline", {"subject": "{subj}"}, False, None),
        ("org.telegram.messenger", "Иванов А.А. (Препод)", "Жду ваши индивидуальные расчетно-графические работы до полуночи на почту", "homework_deadline", {}, False, None),
        ("com.google.android.calendar", "Календарь", "Дедлайн: Предзащита дипломной работы (ауд. {aud})", "defense_deadline", {}, False, None),
        ("org.telegram.messenger", "Учебный сектор", "Кто не загрузил сканы квитанций об оплате обучения, крайний срок — завтра", "tuition_deadline", {}, False, None),
        ("com.moodle.moodlemobile", "Moodle", "Преподаватель выставил оценку за задание 'Лабораторная №{num}' (90/100)", "grade_posted", {"subject": "{subj}"}, True, "Уведомление об оценке задания в обучающей системе Moodle"),
        ("com.github.android", "GitHub", "Review requested on PR #{num} 'lab-{num}-solution'. Deadline: today", "pr_review_deadline", {}, False, None),
        ("org.telegram.messenger", "Группа 302", "Скиньте кто-нибудь титульник для отчета, пока прием не закрыли!", "peer_urgency", {}, False, None),
        ("com.moodle.moodlemobile", "Moodle SPSU", "Внимание: Анкетирование качества преподавания открыто до 30 сентября", "survey_deadline", {}, False, None),
        ("org.telegram.messenger", "Староста", "Не сдал лабу — не допущен к экзамену! Срочно несите долги преподавателю", "debt_warning", {}, False, None),
        ("com.google.android.apps.tasks", "Задачи", "Напоминание: Загрузить рецензию на дипломный проект до 10 июня", "task_deadline", {}, False, None),
    ]

    # --- MESSAGE ---
    message_templates = [
        ("org.telegram.messenger", "{name}", "Привет! Ты завтра идешь на пары или дистанционно?", "personal_chat", {"sender": "{name}"}, False, None),
        ("com.whatsapp", "{name}", "Захвати, пожалуйста, конспекты по {subj}, если не сложно", "personal_chat", {"sender": "{name}"}, False, None),
        ("com.radolyn.ayugram", "Чат проекта", "{name}: Залил новый коммит в ветку develop, проверяйте", "group_chat", {"sender": "{name}"}, False, None),
        ("org.telegram.messenger", "Мама", "Как дела в университете? Обед разогрей в микроволновке", "family_chat", {"sender": "Мама"}, False, None),
        ("com.google.android.apps.messaging", "+7916{num}", "Я подъехал к шлагбауму, выходи", "sms_personal", {"sender": "+7916{num}"}, False, None),
        ("com.vkontakte.android", "{name}", "Скинул тебе в документы шпаргалки к зачету", "social_chat", {"sender": "{name}"}, False, None),
        ("com.discord", "DevServer", "{name}: Ребята, созваниваемся в голосовом канале через 5 минут?", "discord_chat", {"sender": "{name}"}, False, None),
        ("org.telegram.messenger", "Папа", "Машину с ТО забрал, вечером за тобой заеду", "family_chat", {"sender": "Папа"}, False, None),
        ("com.whatsapp", "Родительский чат", "Напоминаем об общем собрании в пятницу в 18:30", "group_announcement", {}, False, None),
        ("org.telegram.messenger", "{name}", "Смотри какой смешной мем нашел в ленте))", "friendly_chat", {"sender": "{name}"}, False, None),
        ("com.google.android.apps.messaging", "Абонент +7999{num}", "Этот абонент снова в сети. Вы можете позвонить ему.", "telecom_availability", {}, False, None),
        ("org.telegram.messenger", "Бот заказов", "Ваш заказ №{num} передан курьеру. Телефон для связи: +7926{num}", "order_bot", {}, True, "Транзакционное сервисное сообщение от чат-бота интернет-магазина"),
        ("com.radolyn.ayugram", "{name}", "Голосовое сообщение (0:45). Нажмите для прослушивания", "voice_message", {"sender": "{name}"}, False, None),
        ("org.telegram.messenger", "Чат дома ТСЖ", "Уважаемые жильцы, завтра с 09:00 до 13:00 плановое отключение воды", "community_chat", {}, False, None),
        ("com.whatsapp", "{name}", "Фотография (1 файл). Посмотри, какой вариант лучше выбрать", "media_message", {"sender": "{name}"}, False, None),
        ("org.telegram.messenger", "Техподдержка", "Оператор {name} ответил на ваше обращение №{num}", "support_chat", {"sender": "Поддержка"}, False, None),
        ("com.vkontakte.android", "Иван Смирнов", "Отправил вам стикер", "sticker_message", {"sender": "Иван Смирнов"}, False, None),
        ("org.telegram.messenger", "{name}", "Договорились, тогда встречаемся на входе в метро в 15:00", "personal_chat", {"sender": "{name}"}, False, None),
        ("com.google.android.apps.messaging", "+373777{num}", "Перезвони мне, срочный вопрос по документам", "sms_personal", {}, False, None),
        ("com.radolyn.ayugram", "Учебный флуд", "{name}: Кто-нибудь уже решил 4-й номер из домашки?", "group_chat", {"sender": "{name}"}, False, None),
    ]

    # --- ADVERTISEMENT ---
    advertisement_templates = [
        ("ru.sberbankmobile", "СберБанк", "Кредит от 4.9%! Оформите заявку онлайн за 2 минуты без визита в офис", "bank_promo", {"merchant": "СберБанк"}, True, "Банковское предложение кредита без движения реальных средств"),
        ("com.idamob.tinkoff.android", "Т-Банк", "Кэшбэк дня до 50% на покупки в ресторанах в эти выходные! Активируйте в приложении", "cashback_promo", {"merchant": "Т-Банк"}, True, "Рекламный пуш об акции с повышенным кэшбэком"),
        ("ru.ozon.app.android", "OZON", "Грандиозная распродажа электроники: скидки до 70% только до полуночи!", "sale_promo", {"merchant": "OZON"}, False, None),
        ("com.dodopizza.app", "Додо Пицца", "Пепперони в подарок по промокоду DODOMIR при заказе от 990 ₽!", "food_promo", {"merchant": "Додо Пицца"}, False, None),
        ("ru.yandex.market", "Яндекс Маркет", "Дарим промокод SALE500 на скидку 500 ₽ при заказе от 2 500 ₽", "coupon_promo", {"merchant": "Яндекс Маркет"}, True, "Содержит числа и рубли, но является рекламным купоном"),
        ("com.letu", "ЛЭТУАЛЬ", "Черная пятница: парфюмерия мировых брендов со скидками до 60%", "cosmetics_promo", {"merchant": "ЛЭТУАЛЬ"}, False, None),
        ("ru.sportmaster.app", "Спортмастер", "Вам начислено 2000 промо-бонусов на покупку кроссовок и экипировки!", "loyalty_points_promo", {"merchant": "Спортмастер"}, True, "Содержит 'начислено 2000 бонусов', риск путаницы с доходом"),
        ("ru.beeline.services", "Билайн", "Тариф 'Всё в одном': 50 Гб интернета и подписка на кинотеатр за 390 руб/мес", "telecom_promo", {"merchant": "Билайн"}, False, None),
        ("ru.mvideo", "М.Видео", "Финальная ликвидация остатков техники! Смартфоны и ноутбуки с выгодой", "retail_promo", {"merchant": "М.Видео"}, False, None),
        ("com.deliveryclub", "Маркет Деливери", "Проголодались? Быстрая доставка любимых блюд со скидкой 20% по коду TASTY", "food_promo", {"merchant": "Маркет Деливери"}, False, None),
        ("ru.sberbankmobile", "Сбер", "Обменяйте бонусы Спасибо на рубли с выгодой до 20% в приложении СберСпасибо", "bonus_exchange_promo", {"merchant": "СберСпасибо"}, True, "Слово 'Перевод' бонусов, маркетинговая акция"),
        ("ru.aliexpress.buyer", "AliExpress", "Всемирный день шопинга 11.11! Миллионы товаров по суперценам", "sale_promo", {"merchant": "AliExpress"}, False, None),
        ("com.mts.mymts", "Мой МТС", "Подключите МТС Premium бесплатно на 30 дней: фильмы, музыка и гигабайты", "subscription_trial_promo", {"merchant": "МТС"}, False, None),
        ("com.wildberries.wbdeti", "Wildberries", "Товары из вашей корзины подешевели! Успейте выкупить по выгодной цене", "cart_discount_promo", {"merchant": "Wildberries"}, False, None),
        ("ru.megafon.mlk", "МегаФон", "Специально для вас: золотой номер телефона в подарок при смене тарифа", "telecom_promo", {"merchant": "МегаФон"}, False, None),
        ("com.samokat.app", "Самокат", "Скидка 150 ₽ на заказ свежих фруктов и выпечки по промокоду FRESH", "groceries_promo", {"merchant": "Самокат"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Откройте брокерский счет и получите акцию стоимостью до 10 000 ₽ в подарок!", "brokerage_promo", {"merchant": "Альфа-Банк"}, True, "Рекламный подарок акцией от банка, нет реального денежного прихода"),
        ("com.yandex.plus", "Яндекс Плюс", "Кинопоиск, Яндекс Музыка и баллы Плюса для всей семьи на 60 дней за 0 ₽", "media_subscription_promo", {"merchant": "Яндекс"}, False, None),
        ("ru.dns.shop.android", "DNS", "Рассрочка 0-0-24 на всю технику Apple и Samsung без первого взноса!", "installment_promo", {"merchant": "DNS"}, False, None),
        ("com.vkontakte.android", "VK Реклама", "Курсы веб-дизайна и программирования со скидкой 40%. Начните бесплатно!", "education_promo", {}, False, None),
    ]

    # --- AUTHENTICATION ---
    auth_templates = [
        ("ru.sberbankmobile", "900", "Код для входа в Сбербанк Онлайн: {code}. Никому не сообщайте пароль.", "login_otp", {"auth_code": "{code}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Код подтверждения операции: {code}. Оплата в интернет-магазине на сумму {amt} ₽.", "tx_confirm_otp", {"auth_code": "{code}", "amount": "{amt}"}, True, "Содержит сумму платежа, но главным действием является ввод 2FA-кода"),
        ("org.telegram.messenger", "Telegram", "Код подтверждения: {code}. Вы можете использовать его для входа в ваш аккаунт.", "login_otp", {"auth_code": "{code}"}, False, None),
        ("ru.gosuslugi.portal", "Gosuslugi", "Код подтверждения входа на портал Госуслуг: {code}. Не сообщайте его никому.", "gov_login_otp", {"auth_code": "{code}"}, False, None),
        ("com.google.android.gms", "Google", "G-{code} — ваш проверочный код безопасности Google.", "google_otp", {"auth_code": "G-{code}"}, False, None),
        ("com.vkontakte.android", "VK ID", "Код для входа: {code}. Если вы не запрашивали код, смените пароль в профиле.", "vk_otp", {"auth_code": "{code}"}, False, None),
        ("com.whatsapp", "WhatsApp", "Ваш проверочный код WhatsApp: {code}. Никому не пересылайте этот код.", "whatsapp_otp", {"auth_code": "{code}"}, False, None),
        ("com.valvesoftware.android.steam.community", "Steam Guard", "Код аутентификации Steam Guard: {steam_code}", "steam_guard_otp", {"auth_code": "{steam_code}"}, False, None),
        ("ru.yandex.auth", "Яндекс ID", "Одноразовый пароль для входа: {code}. Действует 10 минут.", "yandex_otp", {"auth_code": "{code}"}, False, None),
        ("ru.alfabank.mobile.android", "Альфа-Банк", "Пароль для подтверждения смены номера: {code}. Сотрудники банка не запрашивают код.", "security_change_otp", {"auth_code": "{code}"}, False, None),
        ("com.discord", "Discord", "Ваш код безопасности Discord: {code}. Не делитесь им ни с кем.", "discord_otp", {"auth_code": "{code}"}, False, None),
        ("com.apple.android.music", "Apple", "Код проверки Apple ID: {code}. Введите его на вашем устройстве.", "apple_otp", {"auth_code": "{code}"}, False, None),
        ("ru.vtb24.mobilebanking.android", "ВТБ", "Код регистрации нового устройства в ВТБ Онлайн: {code}", "device_register_otp", {"auth_code": "{code}"}, False, None),
        ("com.ftc.faktura.multibank", "Агропромбанк", "Код авторизации APB Online: {code}. Внимание: никому не передавайте!", "apb_otp", {"auth_code": "{code}"}, False, None),
        ("com.wildberries.wbdeti", "Wildberries", "Код для входа в личный кабинет Wildberries: {code}", "wb_otp", {"auth_code": "{code}"}, False, None),
        ("ru.ozon.app.android", "OZON", "Ваш одноразовый код для входа на Ozon: {code}", "ozon_otp", {"auth_code": "{code}"}, False, None),
        ("com.microsoft.authenticator", "Microsoft", "Код безопасности учетной записи Microsoft: {code}", "ms_otp", {"auth_code": "{code}"}, False, None),
        ("com.github.android", "GitHub", "Two-factor authentication code: {code} for login to GitHub.", "github_otp", {"auth_code": "{code}"}, False, None),
        ("ru.sberbankmobile", "СберБизнес", "Одноразовый пароль для подписи платежного поручения: {code}", "payment_sign_otp", {"auth_code": "{code}"}, False, None),
        ("com.idamob.tinkoff.android", "Т-Банк", "Вход выполнен с нового устройства Xiaomi Poco M7. Если это были не вы, нажмите 'Заблокировать'", "new_device_login", {}, True, "Аутентификационный алерт о входе без цифрового кода (security alert)"),
    ]

    # --- OTHER ---
    other_templates = [
        ("com.android.vending", "Google Play", "Доступны обновления для {num} приложений. Нажмите для установки", "app_updates", {}, False, None),
        ("android", "Система Android", "Низкий уровень заряда батареи ({pct}%). Включите режим энергосбережения", "battery_low", {}, False, None),
        ("android", "Система Android", "Батарея полностью заряжена (100%). Отключите зарядное устройство", "battery_full", {}, False, None),
        ("com.android.providers.downloads", "Диспетчер загрузки", "Загрузка файла '{file}' успешно завершена ({mb} МБ)", "download_finished", {}, False, None),
        ("ru.yandex.weatherplugin", "Яндекс Погода", "В ближайший час осадков не ожидается. Температура +{temp}°C", "weather_info", {}, False, None),
        ("com.google.android.apps.photos", "Google Фото", "Воспоминание: посмотрите фотографии за этот день 2 года назад", "photo_memories", {}, False, None),
        ("com.android.systemui", "USB-подключение", "Режим передачи файлов (MTP) активирован. Нажмите для настроек", "usb_status", {}, False, None),
        ("com.android.systemui", "Скриншот сохранен", "Снимок экрана сохранен в галерею. Нажмите для отправки", "screenshot_taken", {}, False, None),
        ("android", "Режим 'Не беспокоить'", "Включен по расписанию до 07:00 утра. Звук уведомлений заглушен", "dnd_active", {}, False, None),
        ("com.google.android.calendar", "Календарь", "Напоминание: Запись к врачу-терапевту через 1 час (в 14:30)", "calendar_event", {}, False, None),
        ("com.google.android.apps.wellbeing", "Цифровое благополучие", "Экранное время за сегодня: 4 ч 12 мин. Достигнут лимит для TikTok", "screen_time_limit", {}, False, None),
        ("com.miui.cleanmaster", "Безопасность", "Найдено 1.4 ГБ мусорных файлов кэша. Нажмите для глубокой очистки", "system_cleanup", {}, False, None),
        ("com.android.settings", "Системное обновление", "Доступно обновление ПО MIUI/HyperOS v15.0.{num}. Размер: 3.2 ГБ", "os_ota_update", {}, False, None),
        ("com.google.android.keep", "Google Keep", "Заметка: купить лампочки E27 и батарейки ААА", "user_note", {}, False, None),
        ("android", "Точка доступа Wi-Fi", "Режим модема активен. Подключено устройств: {num}", "hotspot_active", {}, False, None),
        ("com.xiaomi.scanner", "QR-сканер", "Ссылка успешно распознана: https://spsu.ru/portal", "qr_scanned", {}, False, None),
        ("com.android.bluetooth", "Bluetooth", "Подключены беспроводные наушники 'Redmi Buds 5 Pro' (заряд 90%)", "bt_connected", {}, False, None),
        ("com.wildberries.wbdeti", "Wildberries", "Заказ №{num} готов к выдаче в пункте самовывоза. Срок хранения 7 дней", "pickup_ready", {}, True, "Статус заказа маркетплейса 'готов к выдаче' (логистический статус, не оплата)"),
        ("com.cdek.app", "СДЭК", "Посылка из интернет-магазина поступила в постамат №{num}. Код ячейки: {code}", "parcel_ready", {}, True, "Уведомление доставки с кодом ячейки (не путать с OTP кодом входа)"),
        ("com.google.android.googlequicksearchbox", "Google Ассистент", "Время в пути до дома: 35 минут при текущей дорожной ситуации", "commute_time", {}, False, None),
    ]

    templates_by_cat = {
        "finance.income": income_templates,
        "finance.expense.food": food_templates,
        "finance.expense.transport": transport_templates,
        "finance.expense.shopping": shopping_templates,
        "finance.transfer": transfer_templates,
        "music": music_templates,
        "study.lesson": lesson_templates,
        "study.deadline": deadline_templates,
        "message": message_templates,
        "advertisement": advertisement_templates,
        "authentication": auth_templates,
        "other": other_templates
    }

    # Data dictionary for synthetic variation
    names = ["Алексей В.", "Иван П.", "Елена С.", "Дмитрий К.", "Ольга М.", "Сергей Н.", "Анна Т.", "Максим Р.", "Татьяна Б.", "Денис Ф."]
    companies = ["Яндекс", "СберТех", "Тинькофф Центр", "ВТБ Инновации", "Озон Технологии", "Интерднестрком", "ВкусВилл АйТи", "Студия 15", "Диджитал Решения"]
    shops = ["OZON", "Wildberries", "DNS", "М.Видео", "Lamoda", "Яндекс Маркет", "Золотое Яблоко", "Спортмастер"]
    restaurants = ["Бургер Кинг", "Додо Пицца", "Вкусно и точка", "Токио Сити", "Шоколадница", "Cofix", "Теремок", "Папа Джонс"]
    subjects = ["Высшая математика", "Операционные системы", "Базы данных", "Архитектура ЭВМ", "Теория вероятностей", "Компьютерные сети", "Алгоритмы и структуры данных", "Программирование на Kotlin"]
    artists = ["The Weeknd", "Daft Punk", "Скриптонит", "Miyagi & Эндшпиль", "Queen", "Billie Eilish", "Linkin Park", "Pink Floyd", "Кино", "Король и Шут", "Imagine Dragons", "Coldplay", "Oxxxymiron", "Lofi Fruits Music", "Hans Zimmer"]
    tracks = ["Blinding Lights", "Get Lucky", "Положение", "Minor", "Bohemian Rhapsody", "Bad Guy", "In the End", "Группа крови", "Лесник", "Believer", "Yellow", "Time", "Город под подошвой", "Sunset Lover"]
    files = ["report_sprint1_final.pdf", "presentation_os.pptx", "archive_code_v2.zip", "firmware_patch_35.bin", "dataset_dump.tar.gz"]

    # Fill each category up to target_count
    target_count = 105
    gen_counter = 0

    for cat in TARGET_CATEGORIES:
        current_samples = by_cat[cat]
        needed = target_count - len(current_samples)
        if needed <= 0:
            continue
        
        t_list = templates_by_cat[cat]
        t_idx = 0
        
        for i in range(needed):
            gen_counter += 1
            tpl = t_list[t_idx % len(t_list)]
            t_idx += 1
            
            pkg, t_title, t_text, intent, entities_template, is_hard, hard_reason = tpl
            
            # Context dictionary
            n_idx = (i + gen_counter) % len(names)
            c_idx = (i + gen_counter) % len(companies)
            s_idx = (i + gen_counter) % len(shops)
            r_idx = (i + gen_counter) % len(restaurants)
            sb_idx = (i + gen_counter) % len(subjects)
            sb2_idx = (i + gen_counter + 1) % len(subjects)
            art_idx = (i + gen_counter) % len(artists)
            trk_idx = (i + gen_counter) % len(tracks)
            fl_idx = (i + gen_counter) % len(files)
            
            amt_val = 100 + (i * 73) % 4900
            if amt_val > 1000:
                amt_str = f"{amt_val:,}".replace(",", " ")
            else:
                amt_str = str(amt_val)
                
            bal_val = 12000 + (i * 240) % 95000
            bal_str = f"{bal_val:,}".replace(",", " ")

            fmt_dict = {
                "amt": amt_str,
                "bal": bal_str,
                "comp": companies[c_idx],
                "name": names[n_idx],
                "shop": shops[s_idx],
                "rest": restaurants[r_idx],
                "subj": subjects[sb_idx],
                "subj2": subjects[sb2_idx],
                "artist": artists[art_idx],
                "track": tracks[trk_idx],
                "file": files[fl_idx],
                "card": f"{(1000 + i * 37) % 9999:04d}",
                "num": f"{(100 + i * 19) % 999:03d}",
                "chk": f"{(10 + i * 7) % 99:02d}",
                "aud": f"{100 + (i * 11) % 400}",
                "time": f"{8 + (i % 6):02d}:{(i * 15) % 60:02d}",
                "msg": "Возврат долга" if i % 2 == 0 else "За кофе и обед",
                "branch": f"№{(i % 8) + 1}",
                "cb": f"{20 + (i * 5) % 300}",
                "atm": f"№{(100 + i * 3) % 900}",
                "code": f"{(10000 + i * 83) % 89999 + 10000}",
                "steam_code": f"K{i%9}X{i%7}P",
                "pct": f"{10 + (i % 15)}",
                "mb": f"{12 + (i * 7) % 150}",
                "temp": f"{15 + (i % 14)}"
            }

            rendered_title = t_title.format(**fmt_dict) if t_title else None
            rendered_text = t_text.format(**fmt_dict)
            
            rendered_entities = {}
            for ek, ev in entities_template.items():
                if isinstance(ev, str) and "{" in ev:
                    rendered_entities[ek] = ev.format(**fmt_dict)
                else:
                    rendered_entities[ek] = ev
            
            # If amount in template or text has amt
            if "{amt}" in t_text or "amt" in rendered_entities:
                rendered_entities["amount"] = float(amt_val)
                rendered_entities["currency"] = "PRB" if "PRB" in rendered_text else "RUB"
            if "{code}" in t_text or "code" in rendered_entities:
                rendered_entities["auth_code"] = fmt_dict["code"]

            cat_prefix_map = {
                "finance.income": "fin_inc",
                "finance.expense.food": "fin_food",
                "finance.expense.transport": "fin_transp",
                "finance.expense.shopping": "fin_shop",
                "finance.transfer": "fin_transf",
                "music": "music",
                "study.lesson": "std_les",
                "study.deadline": "std_dl",
                "message": "msg",
                "advertisement": "adv",
                "authentication": "auth",
                "other": "other"
            }
            cat_prefix = cat_prefix_map.get(cat, "event")
            sample_id = f"s1_{cat_prefix}_{len(current_samples) + 1:04d}"

            source_type = "notification"
            if cat == "music" and i % 3 == 0:
                source_type = "mediasession"
            elif cat == "authentication" and i % 2 == 0:
                source_type = "sms"
            elif cat == "other" and i % 3 == 0:
                source_type = "system"

            new_s = {
                "id": sample_id,
                "package_name": pkg,
                "source_type": source_type,
                "title": rendered_title,
                "text": rendered_text,
                "sub_text": None,
                "category": cat,
                "intent": intent,
                "entities": rendered_entities,
                "is_hard_case": is_hard,
                "hard_case_reason": hard_reason,
                "expected_precision_critical": True if (cat.startswith("finance.") or cat == "authentication") else False
            }
            current_samples.append(new_s)

    # Flatten and sort
    final_samples = []
    for cat in TARGET_CATEGORIES:
        final_samples.extend(by_cat[cat])

    print("\n--- Category Distribution in Expanded Golden Dataset ---")
    for cat in TARGET_CATEGORIES:
        cnt = len(by_cat[cat])
        hard_cnt = sum(1 for s in by_cat[cat] if s.get("is_hard_case"))
        print(f"  {cat:<26}: {cnt:>4} samples (Hard cases: {hard_cnt:>2})")

    total_samples = len(final_samples)
    total_hard = sum(1 for s in final_samples if s.get("is_hard_case"))
    print(f"\nTotal Dataset Samples: {total_samples}")
    print(f"Total Hard Cases: {total_hard} ({total_hard/total_samples*100:.1f}%)")

    dataset_obj = {
        "version": "1.1.0",
        "description": "Android Event Engine Sprint 1 Golden Dataset (105+ samples per category, Poco M7 logs & disambiguated hard cases)",
        "target_categories": TARGET_CATEGORIES,
        "samples": final_samples
    }

    with open(current_golden, "w", encoding="utf-8") as f:
        json.dump(dataset_obj, f, ensure_ascii=False, indent=2)

    print(f"\n[SUCCESS] Successfully written {total_samples} samples to {current_golden}")

if __name__ == "__main__":
    generate_all_samples()
