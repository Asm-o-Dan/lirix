package com.example.npc.pipeline.nodes.api.frame

/**
 * Регистровые банки памяти виртуальной машины конвейера.
 */
enum class Bank {
    /** 64-битные целые: Long, Int, Short, Byte, Boolean (0L/1L), Enum ordinals, EpochMillis. */
    LONG,

    /** 64-битные вещественные IEEE 754 (Double, Float bits). */
    DOUBLE,

    /** Иммутабельные ссылки на тяжелые DTO хоста (FinancialTransaction, Category, UserPrototype) и scratch-объекты. */
    REF,

    /** Предвыделенные безаллокационные текстовые регистры с фиксированной емкостью. */
    TEXT
}
