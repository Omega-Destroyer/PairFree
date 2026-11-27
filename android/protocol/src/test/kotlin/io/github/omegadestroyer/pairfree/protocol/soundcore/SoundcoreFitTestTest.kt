package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.FitResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The ear tip fit test of the A3951 family (Soundcore 2.9.9's `0901`). */
class SoundcoreFitTestTest {
    @Test
    fun theModelsExistAndTheTestIsOfferedOnThemAlone() {
        for (m in SoundcoreSession.WEAR_FIT_MODELS) assertTrue(m in SoundcoreSession.SUPPORTED_MODELS, m)
        val s = SoundcoreSession("A3951")
        assertTrue(s.state.hasFitTest)
        val frame = SoundcoreParser(SoundcoreWire.TO_EARBUDS_HEADER).feed(s.startFitTest()!!.single()).single()
        assertEquals(0x0901, frame.command)
        assertEquals(0, frame.body.size)
        assertNull(SoundcoreSession("A3936").startFitTest())
    }

    @Test
    fun aCodeOfZeroOrOneIsAGoodFit() {
        val s = SoundcoreSession("A3951")
        s.startFitTest()
        val body = ByteArray(14) { 0 }.also { it[0] = 1; it[1] = 3 }
        s.receive(SoundcorePacket(0x0901, body))
        assertEquals(FitResult(FitResult.Fit.GOOD, FitResult.Fit.NOT_FIT), s.state.fit)
    }
}
