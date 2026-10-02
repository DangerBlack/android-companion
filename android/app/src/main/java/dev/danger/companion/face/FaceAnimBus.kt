package dev.danger.companion.face

/**
 * Transient animation pose shared between the event burst (writer) and the Glance
 * composition (reader). Deliberately in-memory only: it is not part of the
 * persisted [dev.danger.companion.CompanionState] and never travels on the wire.
 * Every `updateAll` re-composes the widget, so the latest value is picked up.
 */
object FaceAnimBus {
    @Volatile
    var current: BloubAnim = BloubAnim.REST
}
