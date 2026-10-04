package com.eventengine.app.domain

/**
 * Origin of an intercepted OS signal.
 */
enum class EventSource {
    NOTIFICATION,
    MEDIA_SESSION,
    SYSTEM_BROADCAST,
    ACCESSIBILITY,
    MANUAL_INJECTION
}
