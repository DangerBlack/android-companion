package dev.danger.companion.widget

import kotlinx.coroutines.flow.MutableStateFlow

object TextScrollBus {
    val page = MutableStateFlow<String?>(null)
}
