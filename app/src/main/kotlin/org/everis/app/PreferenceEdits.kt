package org.everis.app

/** Main-thread previews stay visible until the newest committed snapshot is rendered.
 * Older writes cannot flash an earlier toggle value during rapid successive taps. */
internal class PreferenceEdits {
    private var nextId = 0L
    private val patches = linkedMapOf<Long, (DiaryState) -> DiaryState>()
    private val writing = mutableSetOf<Long>()

    fun begin(patch: (DiaryState) -> DiaryState): Long = (++nextId).also {
        patches[it] = patch
        writing += it
    }
    fun complete(id: Long, persisted: Boolean) {
        writing -= id
        if (!persisted) patches -= id
    }
    fun preview(state: DiaryState): DiaryState = patches.values.fold(state) { value, patch -> patch(value) }
    fun reconcile(saved: DiaryState, latest: Boolean): DiaryState {
        if (writing.isEmpty() && latest) patches.clear()
        return preview(saved)
    }
}

/** Roll back only preferences; keep navigation, drafts and current async work intact. */
internal fun DiaryState.withPreferencesFrom(saved: DiaryState) = copy(
    palette = saved.palette, themeMode = saved.themeMode, blackTheme = saved.blackTheme,
    hapticsEnabled = saved.hapticsEnabled, newDock = saved.newDock, performanceMode = saved.performanceMode,
    onlyMySubgroup = saved.onlyMySubgroup, subgroup = saved.subgroup, notifications = saved.notifications)
