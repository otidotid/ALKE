package com.otidotid.alke.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object SleepDataRevision {
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    fun changed() { _revision.update { it + 1L } }
}
