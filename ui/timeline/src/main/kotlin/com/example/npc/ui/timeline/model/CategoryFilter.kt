package com.example.npc.ui.timeline.model

import com.example.npc.core.model.classify.Category

enum class CategoryFilter(val category: Category?, val displayName: String = "") {
    ALL(null, "Все"),
    FINANCE(Category.FINANCE, "Финансы"),
    COMMUNICATION(Category.COMMUNICATION, "Связь"),
    MUSIC(Category.MUSIC, "Музыка"),
    SERVICES(Category.SERVICES, "Сервисы"),
    ADVERTISEMENT(Category.ADVERTISEMENT, "Реклама"),
    OTHER(Category.OTHER, "Прочее"),
    UNCLASSIFIED(Category.UNCLASSIFIED, "Без категории");

    constructor(category: Category?) : this(category, category?.name ?: "Все")
}
