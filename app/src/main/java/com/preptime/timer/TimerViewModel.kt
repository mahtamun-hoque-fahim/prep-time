package com.preptime.timer

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.preptime.audio.Bell
import com.preptime.data.store
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val bell = Bell(app)
    private val _controller = MutableStateFlow<TimerController?>(null)
    val controller: StateFlow<TimerController?> = _controller
    val name = MutableStateFlow("")
    val now = MutableStateFlow(SystemClock.elapsedRealtime())

    init {
        bell.raiseVolume()
        viewModelScope.launch {
            val id: String = checkNotNull(saved["id"])
            val t = app.store.templates.first().find { it.id == id } ?: return@launch
            val c = TimerController(t, bell, SystemClock::elapsedRealtime)
            name.value = t.name
            _controller.value = c
            var shown: Any? = null // what the notification shows; repost only when it changes
            while (isActive) {
                c.tick()
                now.value = SystemClock.elapsedRealtime()
                val i = c.activeIndex
                val k = c.clocks.value.getOrNull(i)
                val key = if (c.isLive && k != null) Triple(i, k.phase, k.durationMs) else null
                if (key != shown) {
                    shown = key
                    if (k != null && key != null) TimerService.show(app, "${t.name} · Speaker ${i + 1}", k, now.value)
                    else TimerService.stop(app)
                }
                delay(100)
            }
        }
    }

    override fun onCleared() {
        _controller.value?.close()
        bell.release()
        bell.restoreVolume()
        TimerService.stop(getApplication())
    }
}
