package com.gc.collector.ui.screen

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.CollectorUiStateSnapshotCodec

fun collectorUiStateSaver(): Saver<CollectorUiState, Any> {
    return listSaver(
        save = { state -> CollectorUiStateSnapshotCodec.encode(state) },
        restore = { values -> CollectorUiStateSnapshotCodec.decode(values) },
    )
}
