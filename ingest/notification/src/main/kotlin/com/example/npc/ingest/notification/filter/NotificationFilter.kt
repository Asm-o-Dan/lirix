package com.example.npc.ingest.notification.filter

import android.app.Notification
import com.example.npc.ingest.notification.model.FilterDecision
import com.example.npc.ingest.notification.model.FilterRejectionReason
import com.example.npc.ingest.notification.model.NotificationExtrasData
import com.example.npc.ingest.notification.model.NotificationRawPayload

object NotificationFilter {

    fun evaluate(
        payload: NotificationRawPayload,
        hostPackageName: String
    ): FilterDecision {
        if (isOwnPackage(payload.packageName, hostPackageName)) {
            return FilterDecision(false, FilterRejectionReason.OWN_PACKAGE)
        }
        if (isOngoing(payload.flags)) {
            return FilterDecision(false, FilterRejectionReason.ONGOING_EVENT)
        }
        if (isForegroundService(payload.flags)) {
            return FilterDecision(false, FilterRejectionReason.FOREGROUND_SERVICE)
        }
        if (hasProgressBar(payload.extras)) {
            return FilterDecision(false, FilterRejectionReason.PROGRESS_BAR)
        }
        if (isGroupSummary(payload.flags)) {
            return FilterDecision(false, FilterRejectionReason.GROUP_SUMMARY)
        }
        if (!hasTextContent(payload.extras)) {
            return FilterDecision(false, FilterRejectionReason.EMPTY_CONTENT)
        }
        return FilterDecision(true, null)
    }

    fun isOngoing(flags: Int): Boolean =
        (flags and Notification.FLAG_ONGOING_EVENT) != 0

    fun isForegroundService(flags: Int): Boolean =
        (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

    fun isGroupSummary(flags: Int): Boolean =
        (flags and Notification.FLAG_GROUP_SUMMARY) != 0

    fun hasProgressBar(extras: NotificationExtrasData): Boolean =
        extras.progressMax > 0 || extras.isProgressIndeterminate

    fun isOwnPackage(packageName: String, hostPackageName: String): Boolean =
        packageName == hostPackageName

    fun hasTextContent(extras: NotificationExtrasData): Boolean {
        if (!extras.title.isNullOrBlank()) return true
        if (!extras.text.isNullOrBlank()) return true
        if (!extras.bigText.isNullOrBlank()) return true
        if (extras.textLines.any { it.isNotBlank() }) return true
        if (extras.messagingStyle?.messages?.any { it.text.isNotBlank() } == true) return true
        return false
    }
}
