package org.everis.app

import org.junit.Assert.*
import org.junit.Test

class PreferenceEditsTest {
    @Test fun tapIsVisibleBeforeTheWriteCompletesAndOlderSnapshotsCannotUndoIt() {
        val edits = PreferenceEdits()
        val saved = DiaryState(onlyMySubgroup = false, subgroup = "1")
        val id = edits.begin { it.copy(onlyMySubgroup = true) }
        assertTrue(edits.preview(saved).onlyMySubgroup)
        assertTrue(edits.reconcile(saved, latest = true).onlyMySubgroup)
        edits.complete(id, persisted = true)
        assertTrue(edits.reconcile(saved, latest = false).onlyMySubgroup)
        assertTrue(edits.reconcile(saved.copy(onlyMySubgroup = true), latest = true).onlyMySubgroup)
        assertFalse(edits.reconcile(saved, latest = true).onlyMySubgroup) // A later import may change it.
    }
    @Test fun rapidOppositeTapsKeepTheLatestIntentAcrossAnIntermediateCommit() {
        val edits = PreferenceEdits()
        val saved = DiaryState(hapticsEnabled = false)
        val first = edits.begin { it.copy(hapticsEnabled = true) }
        val last = edits.begin { it.copy(hapticsEnabled = false) }
        edits.complete(first, persisted = true)
        assertFalse(edits.reconcile(saved.copy(hapticsEnabled = true), latest = true).hapticsEnabled)
        edits.complete(last, persisted = true)
        assertFalse(edits.reconcile(saved.copy(hapticsEnabled = true), latest = false).hapticsEnabled)
        assertFalse(edits.reconcile(saved, latest = true).hapticsEnabled)
        assertTrue(edits.reconcile(saved.copy(hapticsEnabled = true), latest = true).hapticsEnabled)
    }
    @Test fun failedWriteRollsBackOnlyItsOwnPreviewWhileAnotherSettingIsStillPending() {
        val edits = PreferenceEdits()
        val saved = DiaryState(newDock = true, performanceMode = false)
        val failed = edits.begin { it.copy(newDock = false) }
        val other = edits.begin { it.copy(performanceMode = true) }
        edits.complete(failed, persisted = false)
        val preview = edits.reconcile(saved, latest = true)
        assertTrue(preview.newDock)
        assertTrue(preview.performanceMode)
        edits.complete(other, persisted = true)
        assertTrue(edits.reconcile(saved.copy(performanceMode = true), latest = true).performanceMode)
    }
    @Test fun noOpWriteSettlesAndDoesNotBlockFutureExternalChanges() {
        val edits = PreferenceEdits()
        val saved = DiaryState(newDock = true)
        val id = edits.begin { it.copy(newDock = true) }
        edits.complete(id, persisted = true)
        assertTrue(edits.reconcile(saved, latest = true).newDock)
        assertFalse(edits.reconcile(saved.copy(newDock = false), latest = true).newDock)
    }
    @Test fun rollbackKeepsTheCurrentNavigationAndDraftState() {
        val current = DiaryState(hapticsEnabled = false, changingGroup = true, requestedDay = "2026-11-23", tasks = mapOf("x" to "draft"))
        val restored = current.withPreferencesFrom(DiaryState(hapticsEnabled = true))
        assertTrue(restored.hapticsEnabled)
        assertTrue(restored.changingGroup)
        assertEquals(current.requestedDay, restored.requestedDay)
        assertEquals(current.tasks, restored.tasks)
    }
}
