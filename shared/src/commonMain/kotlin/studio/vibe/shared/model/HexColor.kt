package studio.vibe.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = HexColorSerializer::class)
data class HexColor(val value: String) {
    companion object {
        fun fromHex(hex: String): HexColor? {
            val cleaned = if (hex.startsWith("#")) hex.drop(1) else hex
            if (cleaned.length != 6 || !cleaned.all { it.isLetterOrDigit() && it in "0123456789abcdefABCDEF" }) return null
            return HexColor("#$cleaned")
        }
    }
}

object HexColorSerializer : KSerializer<HexColor> {
    override val descriptor = PrimitiveSerialDescriptor("HexColor", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: HexColor) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): HexColor {
        val raw = decoder.decodeString()
        return HexColor.fromHex(raw) ?: throw IllegalArgumentException("Invalid hex color: $raw")
    }
}
