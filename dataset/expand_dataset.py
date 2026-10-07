#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script to expand dataset/golden_dataset.json to 100+ verified samples per category (>= 1200 total),
integrating real anonymized Poco M7 device logs and synthetic hard cases.
"""

import json
import os
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# Categories defined in schema.json
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

def load_existing():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    json_path = os.path.join(base_dir, "golden_dataset.json")
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)

# Real device events from Poco M7
POCO_M7_EVENTS = [
    {
        "id": "poco_yandex_music_001",
        "package_name": "ru.yandex.music",
        "source_type": "notification",
        "title": "🄴  2007",
        "text": "Йорш, Дима Сокол",
        "sub_text": None,
        "category": "music",
        "intent": "media_playback",
        "entities": {"artist": "Йорш, Дима Сокол", "track": "2007", "playback_state": "PLAYING"},
        "is_hard_case": True,
        "hard_case_reason": "Наличие Unicode символа [E] (Explicit tag) в названии трека; пришло через Notification, а не MediaSession",
        "expected_precision_critical": False
    },
    {
        "id": "poco_yandex_music_002",
        "package_name": "ru.yandex.music",
        "source_type": "notification",
        "title": "🄴  виски",
        "text": "МОЛОДОСТЬ ВНУТРИ",
        "sub_text": None,
        "category": "music",
        "intent": "media_playback",
        "entities": {"artist": "МОЛОДОСТЬ ВНУТРИ", "track": "виски", "playback_state": "PLAYING"},
        "is_hard_case": True,
        "hard_case_reason": "Unicode Explicit tag [E] в названии трека, кириллическое название",
        "expected_precision_critical": False
    },
    {
        "id": "poco_yandex_music_003",
        "package_name": "ru.yandex.music",
        "source_type": "notification",
        "title": "🄴  If You Say So",
        "text": "Lea Michele",
        "sub_text": None,
        "category": "music",
        "intent": "media_playback",
        "entities": {"artist": "Lea Michele", "track": "If You Say So", "playback_state": "PLAYING"},
        "is_hard_case": True,
        "hard_case_reason": "Unicode Explicit tag [E] в названии трека, латинские метаданные",
        "expected_precision_critical": False
    },
    {
        "id": "poco_yandex_music_004",
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
        "id": "poco_ayugram_tg_001",
        "package_name": "com.radolyn.ayugram",
        "source_type": "notification",
        "title": "Мама",
        "text": "Поделиться ссылкой https://makler.md/ru/odds-and-ends-exchange-and-buy/food-beverage-and-packaging/packaging/an/25782",
        "sub_text": "63 новых сообщения из 8 чатов",
        "category": "message",
        "intent": "personal_message",
        "entities": {"sender": "Мама"},
        "is_hard_case": True,
        "hard_case_reason": "Telegram fork AyuGram (пакет com.radolyn.ayugram без слова 'telegram'), содержит URL и агрегированный subText",
        "expected_precision_critical": True
    },
    {
        "id": "poco_sber_debit_001",
        "package_name": "ru.sberbankmobile",
        "source_type": "notification",
        "title": "Сбербанк Онлайн",
        "text": "💸 Деньги списаны. 1 450 ₽ в пользу Супермаркет. Баланс: 12 300 ₽",
        "sub_text": None,
        "category": "finance.expense.food",
        "intent": "groceries_payment",
        "entities": {"amount": 1450.0, "currency": "RUB", "merchant": "Супермаркет"},
        "is_hard_case": True,
        "hard_case_reason": "Формулировка с эмодзи '💸 Деньги списаны' без стандартного глагола 'Оплата'",
        "expected_precision_critical": True
    },
    {
        "id": "poco_apb_mobile_001",
        "package_name": "com.apb.mobile",
        "source_type": "notification",
        "title": "Агропромбанк",
        "text": "Списание 85.00 PRB. Оплата услуг: Связь IDC. Доступно: 412.50 PRB",
        "sub_text": None,
        "category": "finance.transfer",
        "intent": "utility_payment",
        "entities": {"amount": 85.0, "currency": "PRB", "merchant": "Связь IDC"},
        "is_hard_case": True,
        "hard_case_reason": "Региональный банк ПМР Агропромбанк (пакет com.apb.mobile), валюта PRB (приднестровский рубль)",
        "expected_precision_critical": True
    },
    {
        "id": "poco_apb_mobile_002",
        "package_name": "com.apb.mobile",
        "source_type": "notification",
        "title": "АПБ",
        "text": "Зачисление перевода APB Online: +250.00 PRB от Шестаков В.И.",
        "sub_text": None,
        "category": "finance.income",
        "intent": "transfer_received",
        "entities": {"amount": 250.0, "currency": "PRB", "sender": "Шестаков В.И."},
        "is_hard_case": True,
        "hard_case_reason": "Краткий заголовок 'АПБ', зачисление в валюте PRB",
        "expected_precision_critical": True
    },
    {
        "id": "poco_revanced_yt_001",
        "package_name": "app.revanced.android.youtube",
        "source_type": "notification",
        "title": "YouTube ReVanced",
        "text": "System of a Down — Chop Suey! (Official HD Video)",
        "sub_text": "Воспроизведение",
        "category": "music",
        "intent": "media_playback",
        "entities": {"artist": "System of a Down", "track": "Chop Suey!", "playback_state": "PLAYING"},
        "is_hard_case": True,
        "hard_case_reason": "Пакет app.revanced.android.youtube (форк YouTube), уведомление фонового плеера",
        "expected_precision_critical": False
    },
    {
        "id": "poco_revanced_yt_002",
        "package_name": "app.revanced.android.youtube",
        "source_type": "mediasession",
        "title": "Interstellar Main Theme - Hans Zimmer",
        "text": "Hans Zimmer — Interstellar OST",
        "sub_text": None,
        "category": "music",
        "intent": "media_playback",
        "entities": {"artist": "Hans Zimmer", "track": "Interstellar Main Theme", "playback_state": "PLAYING"},
        "is_hard_case": True,
        "hard_case_reason": "MediaSession перехват из ReVanced, саундтрек без артиста в первичном названии",
        "expected_precision_critical": False
    }
]

print("Script template ready.")
