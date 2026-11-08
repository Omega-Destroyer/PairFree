package io.github.omegadestroyer.pairfree.protocol.widget

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Level
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetLogicTest {
    private val off = NoiseChoice("off")
    private val adaptive = NoiseChoice("anc", Sub.Level(Command.ANC_LEVEL, 17))
    private val indoor = NoiseChoice("anc", Sub.Level(Command.ANC_LEVEL, 18))

    private fun state(mode: String, scene: Int? = null) = EarbudState(
        mapOf(
            Command.ANC_MODE to Value.Number(Command.ANC_MODE.labels.entries.first { it.value == mode }.key),
            Command.ANC_LEVEL to Value.Levels(listOf(17, 18).map { Level(it, it == scene) }),
        ),
    )

    @Test
    fun theSlotsShownFollowTheSize() {
        assertEquals(listOf(0), WidgetGrid.visibleSlots(1, 1))
        assertEquals(listOf(0, 1), WidgetGrid.visibleSlots(2, 1))
        assertEquals(listOf(0, 1, 2), WidgetGrid.visibleSlots(3, 1))
        assertEquals(listOf(0, 1, 2, 3), WidgetGrid.visibleSlots(4, 1))
        assertEquals(listOf(0, 1, 4, 5), WidgetGrid.visibleSlots(2, 2))
        assertEquals((0..7).toList(), WidgetGrid.visibleSlots(4, 2))
        assertEquals(4, WidgetGrid.visibleSlots(9, 1).size)
    }

    @Test
    fun aTwoStateToggleStepsOnlyBetweenItsChoices() {
        val choices = listOf(off, adaptive)
        assertEquals(adaptive, WidgetCycle.nextNoise(choices, state("off"), emptyList()))
        assertEquals(off, WidgetCycle.nextNoise(choices, state("anc", 17), emptyList()))
        // Cancelling in another scene is not the Adaptive choice, so the cycle starts over at its first.
        assertEquals(off, WidgetCycle.nextNoise(choices, state("anc", 18), emptyList()))
        // Transparency is in neither choice.
        assertEquals(off, WidgetCycle.nextNoise(choices, state("transparency"), emptyList()))
    }

    @Test
    fun aSubModeMatchesOnlyItsOwnScene() {
        assertTrue(adaptive.isActive(state("anc", 17), emptyList()))
        assertFalse(adaptive.isActive(state("anc", 18), emptyList()))
        assertFalse(adaptive.isActive(state("off", 17), emptyList()))
        assertTrue(NoiseChoice("anc").isActive(state("anc", 18), emptyList()))
    }

    @Test
    fun aBareModeIsLeftForAMatchingSubMode() {
        val choices = listOf(NoiseChoice("anc"), indoor, off)
        assertEquals(off, WidgetCycle.nextNoise(choices, state("anc", 18), emptyList()))
        assertEquals(indoor, WidgetCycle.nextNoise(choices, state("anc", 17), emptyList()))
    }

    @Test
    fun brandRowsMatchByKeyAndOption() {
        val on = NoiseChoice("anc", Sub.Row("anc_scene", "indoor"))
        val rows = listOf<Setting>(Setting.Choice("anc_scene", "indoor", listOf("adaptive", "indoor")))
        assertTrue(on.isActive(state("anc"), rows))
        assertFalse(on.isActive(state("anc"), listOf(Setting.Choice("anc_scene", "adaptive", listOf("adaptive", "indoor")))))
        assertFalse(on.isActive(state("anc"), emptyList()))
    }

    @Test
    fun anEqToggleStepsThroughItsProfiles() {
        val ids = listOf("builtin:stock", "preset:Bass boost", "user:Mine")
        assertEquals("preset:Bass boost", WidgetCycle.nextEq(ids, "builtin:stock"))
        assertEquals("builtin:stock", WidgetCycle.nextEq(ids, "user:Mine"))
        assertEquals("builtin:stock", WidgetCycle.nextEq(ids, "something else"))
        assertEquals("builtin:stock", WidgetCycle.nextEq(ids, null))
    }
}
