#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Lirix PR Engine: Automated Multi-Platform Social Media Publisher
Powered by charlie947/social-media-skills methodology & MCP LinkedIn.
"""

import sys
import os
import json
import argparse
from pathlib import Path

# Paths
BASE_DIR = Path(__file__).resolve().parent.parent.parent
BRAIN_DIR = Path("C:/Users/DaniilTuT/.gemini/antigravity/brain/32389d24-0cd3-4c9a-aab7-af3d631a6961")

POSTS = {
    "release": {
        "id": "POST-01-RELEASE",
        "title": "Релиз Lirix v1.2.1 и боль стримингов",
        "framework": "PAS (Problem - Agitation - Solution)",
        "image": str(BRAIN_DIR / "screen_live_vinyl.png"),
        "text": """Почему современные музыкальные стриминги прячут тексты песен?

Официальные приложения превращают караоке в платную роскошь:
- Тексты песен часто недоступны оффлайн.
- Если таймингов нет в базе — вы не можете их добавить.
- Вы не владеете историей своих прослушиваний.

Я создал Lirix — открытый музыкальный хаб для Android.

Что умеет Lirix v1.2.1:
1. Автоматически подхватывает трек из любого плеера (Spotify, Яндекс Музыка, VK, Telegram).
2. Находит оффлайн-караоке и аккорды для гитары прямо во время воспроизведения.
3. LrcTapSync Studio: если трек редкий, вы просто тапаете по строкам в такт песни и создаете караоке за 2 минуты.
4. Полная кастомизация: темы Spotify, Яндекс Музыки, Обсидиан и Пастель.

Приложение полностью бесплатное, без рекламы и телеметрии.

Релиз v1.2.1 и исходный код доступны на GitHub:
https://github.com/Asm-o-Dan/lirix/releases/tag/v1.2.1

#android #jetpackcompose #opensource #kotlin #music #karaoke #github"""
    },
    "tech": {
        "id": "POST-02-TECH-120FPS",
        "title": "120 FPS в Jetpack Compose: архитектура аналогового винила",
        "framework": "SLAY / Tech",
        "image": str(BRAIN_DIR / "screen_live_vinyl.png"),
        "text": """Как заставить виниловую пластинку в Jetpack Compose крутиться на 120 FPS без просадки батареи?

Когда я проектировал плеер Lirix, передо мной стояла задача сделать аналоговый винил с физическим звукоснимателем (Tonearm), реагирующим на свайпы.

В первых сборках частые рекомпозиции таймера вызывали микрофризы.

Архитектурные решения:
1. Изоляция тиков: таймер интерполируется через withFrameMillis внутри LaunchedEffect с детерминированной привязкой к позиции контроллера.
2. Canvas Tonearm: головка картриджа и игла рассчитываются на Canvas с вынесенными за пределы рендера тригонометрическими координатами.
3. Мемоизация кистей: градиенты Brush.radialGradient и Brush.sweepGradient закешированы через remember(tokens), обеспечивая мгновенный ре-теминг без лагов.
4. Жесты: свайпы смены трека и двойной тап для паузы работают независимо от скролла караоке.

Кодовая база открыта под лицензией MIT:
https://github.com/Asm-o-Dan/lirix

#androiddev #jetpackcompose #mobiledev #performance #kotlin #opensource"""
    },
    "wrapped": {
        "id": "POST-03-WRAPPED",
        "title": "Музыкальный Wrapped 24/7 без серверов",
        "framework": "AIDA (Attention - Interest - Desire - Action)",
        "image": str(BRAIN_DIR / "screen_nowplaying_new.png"),
        "text": """Зачем ждать декабря, чтобы узнать свои любимые треки года?

В Lirix статистика прослушиваний работает в реальном времени прямо на вашем устройстве:
- Индекс одержимости: алгоритм вычисляет треки, которые вы переслушиваете чаще всего.
- Компас активности: анализирует пики прослушивания по часам суток и дням недели.
- 26 игровых достижений: от «Ночного меланхолика» до «Аудио-марафона».
- Генератор карточек для Stories (9:16) и публикаций (1:1): делитесь любимой музыкой в один клик.

Все расчеты выполняются локально в Room SQLite. Ваша приватность защищена на 100%.

Скачать актуальный релиз:
https://github.com/Asm-o-Dan/lirix/releases/tag/v1.2.1

#privacy #android #musicstats #spotifywrapped #yandexmusic #localfirst"""
    }
}

def list_posts():
    print("=== Lirix PR Engine: Available Content Matrix ===")
    for key, p in POSTS.items():
        print(f"[{key}] {p['id']} - {p['title']}")
        print(f"    Framework: {p['framework']}")
        print(f"    Image: {p['image']}")
        print(f"    Length: {len(p['text'])} chars")
        print()

def get_post_json(key):
    if key not in POSTS:
        print(f"Error: Unknown post key '{key}'", file=sys.stderr)
        sys.exit(1)
    return json.dumps(POSTS[key], ensure_ascii=False, indent=2)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Lirix Social Media Automation Engine")
    parser.add_argument("action", choices=["list", "get"], help="Action to perform")
    parser.add_argument("--post", choices=list(POSTS.keys()), help="Target post key")
    args = parser.parse_args()

    if args.action == "list":
        list_posts()
    elif args.action == "get":
        if not args.post:
            print("Please specify --post <key>")
            sys.exit(1)
        print(get_post_json(args.post))
