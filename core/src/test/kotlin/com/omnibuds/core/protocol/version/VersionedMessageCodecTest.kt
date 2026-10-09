package com.omnibuds.core.protocol.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionedMessageCodecTest {

    // Fixture command/response
    data class AncCommand(val mode: Int)
    data class AncResponse(val activeMode: Int, val batteryLevel: Int? = null)

    // Codec for Protocol Version 1 (single-byte ANC payload)
    class V1AncCodec : VersionedMessageCodec<AncCommand, AncResponse> {
        override val protocolVersion = ProtocolVersion.Semantic(1, 0, 0)

        override fun encodeCommand(command: AncCommand): ByteArray {
            return byteArrayOf(0x01, command.mode.toByte())
        }

        override fun decodeResponse(payload: ByteArray): AncResponse? {
            if (payload.size < 2 || payload[0] != 0x01.toByte()) return null
            return AncResponse(activeMode = payload[1].toInt())
        }
    }

    // Codec for Protocol Version 2 (extended payload with battery level)
    class V2AncCodec : VersionedMessageCodec<AncCommand, AncResponse> {
        override val protocolVersion = ProtocolVersion.Semantic(2, 0, 0)

        override fun encodeCommand(command: AncCommand): ByteArray {
            return byteArrayOf(0x02, command.mode.toByte(), 0x00)
        }

        override fun decodeResponse(payload: ByteArray): AncResponse? {
            if (payload.size < 3 || payload[0] != 0x02.toByte()) return null
            return AncResponse(activeMode = payload[1].toInt(), batteryLevel = payload[2].toInt())
        }
    }

    @Test
    fun version1CodecEncodingAndDecoding() {
        val codec = V1AncCodec()
        val bytes = codec.encodeCommand(AncCommand(mode = 2))
        assertEquals(2, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])

        val response = codec.decodeResponse(byteArrayOf(0x01, 0x02))
        assertEquals(AncResponse(activeMode = 2, batteryLevel = null), response)

        // V2 frame fails on V1 codec
        assertNull(codec.decodeResponse(byteArrayOf(0x02, 0x02, 0x50)))
        // Truncated frame returns null
        assertNull(codec.decodeResponse(byteArrayOf(0x01)))
    }

    @Test
    fun version2CodecEncodingAndDecoding() {
        val codec = V2AncCodec()
        val bytes = codec.encodeCommand(AncCommand(mode = 2))
        assertEquals(3, bytes.size)
        assertEquals(0x02.toByte(), bytes[0])

        val response = codec.decodeResponse(byteArrayOf(0x02, 0x02, 0x50))
        assertEquals(AncResponse(activeMode = 2, batteryLevel = 80), response)

        // V1 frame fails on V2 codec
        assertNull(codec.decodeResponse(byteArrayOf(0x01, 0x02)))
        // Truncated frame returns null
        assertNull(codec.decodeResponse(byteArrayOf(0x02, 0x02)))
    }
}
