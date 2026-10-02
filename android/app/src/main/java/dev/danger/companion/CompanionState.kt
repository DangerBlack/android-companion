package dev.danger.companion

import org.json.JSONObject

data class CompanionState(
    val emotion: Emotion = Emotion.SLEEPY,
    val text: String? = null,
    val instanceId: String = "default",
    val instanceLabel: String = "Companion",
    val colorArgb: Int = DEFAULT_COLOR,
    val updatedAt: Long = 0L,
    val ttlMs: Long? = null,
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("v", 1)

        val instance = JSONObject()
        instance.put("id", instanceId)
        instance.put("label", instanceLabel)
        instance.put("color", colorToHex(colorArgb))
        root.put("instance", instance)

        root.put("emotion", emotion.name.lowercase())
        if (text != null) root.put("text", text) else root.put("text", JSONObject.NULL)
        root.put("updatedAt", updatedAt)
        root.put("ts", updatedAt)
        if (ttlMs == null) root.put("ttl_ms", JSONObject.NULL) else root.put("ttl_ms", ttlMs)
        return root.toString()
    }

    companion object {
        const val DEFAULT_COLOR: Int = 0xFF4F9CF9.toInt()

        fun defaultSleepy(): CompanionState = CompanionState(
            emotion = Emotion.SLEEPY,
            text = "zzz",
            instanceId = "default",
            instanceLabel = "Companion",
            colorArgb = DEFAULT_COLOR,
            updatedAt = 0L,
            ttlMs = null,
        )

        fun parseColor(value: String?): Int = hexToColor(value)

        fun fromJson(raw: String?): CompanionState? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = JSONObject(raw)
                val instance = root.optJSONObject("instance")
                CompanionState(
                    emotion = Emotion.fromString(root.optString("emotion", null)),
                    text = if (root.isNull("text")) null else root.optString("text", null),
                    instanceId = instance?.optString("id") ?: "default",
                    instanceLabel = instance?.optString("label") ?: "Companion",
                    colorArgb = hexToColor(instance?.optString("color")),
                    updatedAt = if (root.has("updatedAt")) {
                        root.optLong("updatedAt", 0L)
                    } else {
                        root.optLong("ts", 0L)
                    },
                    ttlMs = if (root.isNull("ttl_ms")) null else root.optLong("ttl_ms"),
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun colorToHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

        private fun hexToColor(hex: String?): Int {
            if (hex.isNullOrBlank()) return DEFAULT_COLOR
            return try {
                val cleaned = hex.trim().removePrefix("#")
                val rgb = when (cleaned.length) {
                    6 -> cleaned.toLong(16)
                    8 -> cleaned.substring(2).toLong(16)
                    else -> return DEFAULT_COLOR
                }
                (0xFF000000L or rgb).toInt()
            } catch (_: Exception) {
                DEFAULT_COLOR
            }
        }
    }
}
