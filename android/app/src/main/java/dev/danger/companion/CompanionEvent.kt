package dev.danger.companion

import org.json.JSONObject

data class CompanionEvent(
    val ts: Long,
    val emotion: Emotion,
    val text: String?,
    val instanceId: String,
    val instanceLabel: String,
    val colorArgb: Int,
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("ts", ts)
        root.put("emotion", emotion.name.lowercase())
        if (text != null) root.put("text", text) else root.put("text", JSONObject.NULL)
        root.put("instanceId", instanceId)
        root.put("instanceLabel", instanceLabel)
        root.put("colorArgb", colorArgb)
        return root.toString()
    }

    companion object {
        fun fromJson(raw: String?): CompanionEvent? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = JSONObject(raw)
                CompanionEvent(
                    ts = root.optLong("ts", 0L),
                    emotion = Emotion.fromString(root.optString("emotion", null)),
                    text = if (root.isNull("text")) null else root.optString("text", null),
                    instanceId = root.optString("instanceId", "default"),
                    instanceLabel = root.optString("instanceLabel", "Companion"),
                    colorArgb = root.optInt("colorArgb", CompanionState.DEFAULT_COLOR),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
