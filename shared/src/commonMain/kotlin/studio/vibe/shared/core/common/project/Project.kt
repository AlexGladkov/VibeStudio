package studio.vibe.shared.core.common.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.HexColor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Custom serializer for [Instant] that handles both:
 * - ISO 8601 string format (Kotlin native)
 * - Double (Swift's `timeIntervalSinceReferenceDate`, seconds since 2001-01-01)
 */
@OptIn(ExperimentalTime::class)
object SwiftCompatInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    /** Swift reference date: 2001-01-01T00:00:00Z in Unix epoch seconds. */
    private const val SWIFT_REFERENCE_EPOCH = 978307200L

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                if (element.isString) {
                    return Instant.parse(element.content)
                }
                // Numeric: Swift's timeIntervalSinceReferenceDate
                val swiftSeconds = element.double
                val unixSeconds = swiftSeconds.toLong() + SWIFT_REFERENCE_EPOCH
                val nanos = ((swiftSeconds % 1.0) * 1_000_000_000).toLong()
                return Instant.fromEpochSeconds(unixSeconds, nanos)
            }
        }
        // Fallback: try as ISO 8601 string
        return Instant.parse(decoder.decodeString())
    }
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Serializable
data class Project(
    val id: Uuid = Uuid.random(),
    val name: String,
    val path: FilePath,
    val color: HexColor? = null,
    @Serializable(with = SwiftCompatInstantSerializer::class)
    val lastOpened: Instant = Clock.System.now(),
    val shellPath: String = "/bin/zsh",
    val productionURL: String? = null,
)
