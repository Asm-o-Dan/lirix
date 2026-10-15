package com.lirix.app.domain

/**
 * Category taxonomy for events, aligned with Architecture specification and QA Golden Dataset.
 */
enum class Category(val key: String) {
    FINANCE_INCOME("finance.income"),
    FINANCE_EXPENSE_FOOD("finance.expense.food"),
    FINANCE_EXPENSE_TRANSPORT("finance.expense.transport"),
    FINANCE_EXPENSE_SHOPPING("finance.expense.shopping"),
    FINANCE_TRANSFER("finance.transfer"),
    MUSIC("music"),
    STUDY_LESSON("study.lesson"),
    STUDY_DEADLINE("study.deadline"),
    MESSAGE("message"),
    ADVERTISEMENT("advertisement"),
    AUTHENTICATION("authentication"),
    OTHER("other"),
    UNKNOWN("unknown");

    fun isFinance(): Boolean {
        return this in setOf(
            FINANCE_INCOME,
            FINANCE_EXPENSE_FOOD,
            FINANCE_EXPENSE_TRANSPORT,
            FINANCE_EXPENSE_SHOPPING,
            FINANCE_TRANSFER
        )
    }

    fun isStudy(): Boolean {
        return this in setOf(
            STUDY_LESSON,
            STUDY_DEADLINE
        )
    }

    companion object {
        fun fromKey(key: String): Category {
            val normalized = key.trim().lowercase()
            return entries.firstOrNull { it.key == normalized } ?: UNKNOWN
        }
    }
}
