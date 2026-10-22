package com.neurasamu.build.samu_brid.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val time: String, val level: String, val message: String)

object LogRepo {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun add(level: String, message: String) {
        val e = LogEntry(fmt.format(Date()), level, message)
        _logs.update { (it + e).takeLast(500) }
    }
    fun info(m: String) = add("INFO", m)
    fun warn(m: String) = add("WARN", m)
    fun error(m: String) = add("ERROR", m)
    fun clear() { _logs.value = emptyList() }
}
