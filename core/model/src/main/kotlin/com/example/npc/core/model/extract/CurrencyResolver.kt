package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode

/**
 * Контракт контекстного разрешения валюты.
 */
interface CurrencyResolver {
    fun resolve(token: String, contextPackage: String? = null): CurrencyCode?
}
