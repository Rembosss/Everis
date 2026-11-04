package org.everis.app

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/** Stops while backgrounded. Read the value only in the smallest component that needs it. */
@Composable
fun rememberLiveInstant(periodMillis: Long = 1000): State<Instant> {
    val owner = LocalLifecycleOwner.current
    return produceState(Instant.now(), owner, periodMillis) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(periodMillis - Math.floorMod(System.currentTimeMillis(), periodMillis))
            }
        }
    }
}
