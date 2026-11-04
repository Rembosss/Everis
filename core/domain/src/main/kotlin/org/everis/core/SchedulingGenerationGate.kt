package org.everis.core

/** Stops a delayed scheduling callback from replacing work for newer saved settings. */
class SchedulingGenerationGate {
    private val latest = mutableMapOf<String, Long>()
    @Synchronized fun accept(scope: String, generation: Long): Boolean {
        require(generation >= 0)
        val previous = latest[scope]
        if (previous != null && generation < previous) return false
        latest[scope] = generation
        return true
    }
}
