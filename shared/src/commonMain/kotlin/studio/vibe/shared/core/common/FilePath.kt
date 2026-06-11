package studio.vibe.shared.core.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer that handles both Kotlin format `{"path":"/foo"}` and
 * Swift format `"file:///foo"` or `"/foo"` (plain string).
 */
object FilePathSerializer : KSerializer<FilePath> {
    override val descriptor = PrimitiveSerialDescriptor("FilePath", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FilePath) {
        encoder.encodeString(value.path)
    }

    override fun deserialize(decoder: Decoder): FilePath {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            val raw = when {
                element is JsonPrimitive && element.isString -> element.content
                else -> {
                    // Object form: {"path": "..."}
                    element.jsonObject["path"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("FilePath object missing 'path' key")
                }
            }
            return FilePath(normalizeFileUrl(raw))
        }
        // Fallback for non-JSON decoders
        return FilePath(normalizeFileUrl(decoder.decodeString()))
    }

    /** Strips `file:///` prefix and trailing slash from Swift-format file URLs. */
    private fun normalizeFileUrl(raw: String): String {
        val stripped = if (raw.startsWith("file:///")) {
            "/" + raw.removePrefix("file:///")
        } else if (raw.startsWith("file://")) {
            raw.removePrefix("file://")
        } else {
            raw
        }
        return stripped.trimEnd('/')
    }
}

@Serializable(with = FilePathSerializer::class)
data class FilePath(val path: String) {
    val name: String get() = path.substringAfterLast('/')
    val parent: FilePath get() = FilePath(path.substringBeforeLast('/', ""))

    fun child(name: String): FilePath = FilePath("$path/$name")

    override fun toString(): String = path
}
