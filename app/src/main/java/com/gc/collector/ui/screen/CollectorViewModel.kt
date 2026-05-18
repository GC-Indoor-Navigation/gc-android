package com.gc.collector.ui.screen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CollectorViewModel : ViewModel() {
    private val _screenState = MutableStateFlow(CollectorScreenState())
    val screenState: StateFlow<CollectorScreenState> = _screenState.asStateFlow()
}
