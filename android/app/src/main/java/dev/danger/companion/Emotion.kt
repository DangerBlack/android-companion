package dev.danger.companion

enum class Emotion {
    NEUTRAL,
    HAPPY,
    THINKING,
    ERROR,
    SLEEPY,
    LISTENING;

    companion object {
        fun fromString(value: String?): Emotion {
            if (value.isNullOrBlank()) return NEUTRAL
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: NEUTRAL
        }
    }
}
