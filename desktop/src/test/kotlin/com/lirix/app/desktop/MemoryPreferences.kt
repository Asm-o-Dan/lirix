package com.lirix.app.desktop

import java.util.prefs.AbstractPreferences

/** Isolated preferences implementation: never opens Windows user registry nodes. */
internal class MemoryPreferences(parent: AbstractPreferences? = null, name: String = "") : AbstractPreferences(parent, name) {
    private val values = mutableMapOf<String, String>()
    private val nodes = mutableMapOf<String, MemoryPreferences>()
    override fun putSpi(key: String, value: String) { values[key] = value }
    override fun getSpi(key: String): String? = values[key]
    override fun removeSpi(key: String) { values.remove(key) }
    override fun removeNodeSpi() { values.clear(); nodes.clear() }
    override fun keysSpi(): Array<String> = values.keys.toTypedArray()
    override fun childrenNamesSpi(): Array<String> = nodes.keys.toTypedArray()
    override fun childSpi(name: String): AbstractPreferences = nodes.getOrPut(name) { MemoryPreferences(this, name) }
    override fun syncSpi() = Unit
    override fun flushSpi() = Unit
}
