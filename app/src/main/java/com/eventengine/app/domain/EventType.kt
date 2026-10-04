package com.eventengine.app.domain

/**
 * Functional classification of an event.
 */
enum class EventType {
    TRANSACTION,
    MESSAGING_CHAT,
    MEDIA_PLAYBACK,
    NAVIGATION,
    CALENDAR_REMINDER,
    SYSTEM_ALERT,
    AUTHENTICATION_OTP,
    UNKNOWN
}
