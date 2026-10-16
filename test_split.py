# -*- coding: utf-8 -*-
import re

raw = "| Куплет |: Gm A#A ну давай ломися и дави на педаль C7 D7A то не попадешь на супермодный фестиваль Gm A#Где рвут гитары и в гардеробы сдают шубы C7 D7Фест под названием «вытаращи зубы»."

GLUED_WORD_CHORD_REGEX = re.compile(r'([а-яёa-zА-ЯЁA-Z0-9,\.\!\?])([A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)+\b)')
GLUED_CHORD_WORD_REGEX = re.compile(r'\b([A-H](?:[#b](?:m|maj|min|dim|aug|sus|add|\d)*|(?:m|maj|min|dim|aug|sus|add|\d)+))([А-ЯЁа-яёA-Za-z])')
BARE_CHORD_CAP_REGEX = re.compile(r'\b([A-H])([А-ЯЁ])')
CHORD_INTERNAL_SPACE_REGEX = re.compile(r'\b([A-H][#b]?)\s+([m\d])\b')
SECTION_HEADER_SPLIT_REGEX = re.compile(r'(?<=\S)\s*(\|[^|\n]+\|:?|\[[^\]\n]+\])', re.IGNORECASE)
SECTION_HEADER_AFTER_REGEX = re.compile(r'(\|[^|\n]+\|:?|\[[^\]\n]+\])\s*(?=\S)', re.IGNORECASE)
PUNCTUATION_SPLIT_REGEX = re.compile(r'([.!?»”])\s+(?=[А-ЯЁA-Z]|(?:[A-H][#b]?))')

CHORD_TOKEN = r'(?:[A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*)'
CHORD_CLUSTER = rf'(?:{CHORD_TOKEN}(?:\s+{CHORD_TOKEN})*)'
CAP_START = r'(?:[А-ЯЁA-Z](?:\s+[а-яёa-z]+|[а-яёa-z]+))'

CHORD_BEFORE_CAP_REGEX = re.compile(rf'({CHORD_CLUSTER})\s+({CAP_START})')
WORD_BEFORE_CHORD_REGEX = re.compile(rf'([а-яёa-z,\.\!\?])\s+({CHORD_CLUSTER})')

t = raw
t = GLUED_WORD_CHORD_REGEX.sub(r'\1 \2', t)
t = GLUED_CHORD_WORD_REGEX.sub(r'\1 \2', t)
t = BARE_CHORD_CAP_REGEX.sub(r'\1 \2', t)
t = CHORD_INTERNAL_SPACE_REGEX.sub(r'\1\2', t)
t = SECTION_HEADER_SPLIT_REGEX.sub(r'\n\n\1\n', t)
t = SECTION_HEADER_AFTER_REGEX.sub(r'\1\n', t)
t = PUNCTUATION_SPLIT_REGEX.sub(r'\1\n', t)
t = CHORD_BEFORE_CAP_REGEX.sub(r'\1\n\2', t)
t = WORD_BEFORE_CHORD_REGEX.sub(r'\1\n\2', t)

lines = [l.strip() for l in t.split('\n') if l.strip()]
for idx, l in enumerate(lines):
    print(f"L{idx}: {l}")
