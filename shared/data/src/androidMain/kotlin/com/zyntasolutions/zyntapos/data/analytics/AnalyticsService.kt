package com.zyntasolutions.zyntapos.data.analytics

import android.content.Context
import android.os.Bundle
import co.touchlab.kermit.Logger
import com.google.firebase.analytics.FirebaseAnalytics
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker

/**
 * Android implementation of [AnalyticsService] using Firebase Analytics SDK.
 *
 * Firebase Analytics SDK is Android-only (TODO-011 rule #1: Firebase SDK must only
 * be used in androidMain — never in commonMain).
 *
 * Events are logged to the linked GA4 property for unified cross-platform dashboard.
 */
actual class AnalyticsService(context: Context) : AnalyticsTracker {

    private val log = Logger.withTag("AnalyticsService")
    private val firebaseAnalytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    actual override fun logEvent(name: String, params: Map<String, String>) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) -> putString(key, value) }
        }
        firebaseAnalytics.logEvent(name, bundle)
        log.d { "Analytics event: $name params=$params" }
    }

    actual override fun logScreenView(screenName: String, screenClass: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            if (screenClass.isNotBlank()) {
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        log.d { "Analytics screen: $screenName" }
    }

    actual override fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
        log.d { "Analytics userId: ${userId ?: "(cleared)"}" }
    }

    actual override fun setUserProperty(name: String, value: String) {
        firebaseAnalytics.setUserProperty(name, value)
        log.d { "Analytics property: $name=$value" }
    }
}
