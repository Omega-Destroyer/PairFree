package io.github.omegadestroyer.pairfree.protocol

/**
 * Reassembles frames from a byte stream. Feed it whatever each socket read returns;
 * one read is not one frame. Bytes before a frame start (`FF`) are dropped and a
 * partial frame waits for the rest. Same rules as `parse_stream` in tools/probe.py.
 */
public class FrameParser {
    private var buffer = ByteArray(0)

    /** Bytes held back, waiting for the rest of a frame. */
    public val pending: ByteArray get() = buffer.copyOf()

    public fun feed(bytes: ByteArray): List<Frame> {
        var buf = buffer + bytes
        val frames = ArrayList<Frame>()
        while (true) {
            val start = buf.indexOf(Wire.SOF.toByte())
            if (start < 0) {
                buf = ByteArray(0)
                break
            }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < Frame.HEADER_SIZE) break
            val end = Frame.HEADER_SIZE + (buf[3].toInt() and 0xFF)
            if (buf.size < end) break
            frames += Frame(buf.u16(4), buf.u16(6), buf.copyOfRange(Frame.HEADER_SIZE, end))
            buf = buf.copyOfRange(end, buf.size)
        }
        buffer = buf
        return frames
    }

    /** Drops anything held back, for example after reconnecting. */
    public fun reset() {
        buffer = ByteArray(0)
    }
}
