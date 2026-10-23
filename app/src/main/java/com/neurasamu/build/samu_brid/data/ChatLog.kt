package com.neurasamu.build.samu_brid.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ChatLog {
    enum class Kind { IN, OUT, ERR }
    data class Entry(val kind: Kind, val text: String, val source: String, val at: Long)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun add(kind: Kind, text: String, source: String) {
        _entries.update { (it + Entry(kind, text, source, System.currentTimeMillis())).takeLast(200) }
    }
    fun clear() { _entries.value = emptyList() }
    fun lastOut(): String? = _entries.value.lastOrNull { it.kind == Kind.OUT }?.text
}
