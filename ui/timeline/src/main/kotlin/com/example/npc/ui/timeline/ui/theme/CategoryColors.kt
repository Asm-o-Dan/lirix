package com.example.npc.ui.timeline.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.npc.core.model.classify.Category

data class CategoryStyle(
    val label: String,
    val primaryColor: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val borderColor: Color
)

object CategoryColors {
    // 1. Финансы — изумрудный (Emerald Green)
    val Finance = CategoryStyle(
        label = "Финансы",
        primaryColor = Color(0xFF059669),
        containerColor = Color(0xFFD1FAE5),
        onContainerColor = Color(0xFF065F46),
        borderColor = Color(0xFF10B981)
    )

    // 2. Связь — насыщенный синий (Royal Blue)
    val Communication = CategoryStyle(
        label = "Связь",
        primaryColor = Color(0xFF2563EB),
        containerColor = Color(0xFFDBEAFE),
        onContainerColor = Color(0xFF1E40AF),
        borderColor = Color(0xFF3B82F6)
    )

    // 3. Музыка / Медиа — фиолетовый (Purple / Violet)
    val Music = CategoryStyle(
        label = "Музыка",
        primaryColor = Color(0xFF7C3AED),
        containerColor = Color(0xFFEDE9FE),
        onContainerColor = Color(0xFF5B21B6),
        borderColor = Color(0xFF8B5CF6)
    )

    // 4. Сервисы — тёплый оранжевый (Vibrant Orange)
    val Services = CategoryStyle(
        label = "Сервисы",
        primaryColor = Color(0xFFEA580C),
        containerColor = Color(0xFFFFEDD5),
        onContainerColor = Color(0xFF9A3412),
        borderColor = Color(0xFFF97316)
    )

    // 5. Реклама — сланцево-красный (Slate/Crimson)
    val Advertisement = CategoryStyle(
        label = "Реклама",
        primaryColor = Color(0xFFDC2626),
        containerColor = Color(0xFFFEE2E2),
        onContainerColor = Color(0xFF991B1B),
        borderColor = Color(0xFFEF4444)
    )

    // 6. Неизвестно / Прочее — нейтральный серый (Slate Neutral)
    val Unknown = CategoryStyle(
        label = "Неизвестно",
        primaryColor = Color(0xFF64748B),
        containerColor = Color(0xFFF1F5F9),
        onContainerColor = Color(0xFF334155),
        borderColor = Color(0xFF94A3B8)
    )

    // Специальные цвета статусов
    val DeclinedBadgeContainer = Color(0xFFFEE2E2)
    val DeclinedBadgeContent = Color(0xFF991B1B)
    val DeclinedBadgeBorder = Color(0xFFEF4444)

    val ExpenseAmount = Color(0xFFDC2626) // Красный оттенок для списаний
    val IncomeAmount = Color(0xFF16A34A)  // Зелёный оттенок для зачислений
    val TransferAmount = Color(0xFF2563EB) // Синий оттенок для переводов

    fun forCategory(category: Category): CategoryStyle = when (category) {
        Category.FINANCE -> Finance
        Category.COMMUNICATION -> Communication
        Category.MUSIC -> Music
        Category.SERVICES -> Services
        Category.ADVERTISEMENT -> Advertisement
        Category.OTHER,
        Category.UNCLASSIFIED -> Unknown
    }
}
