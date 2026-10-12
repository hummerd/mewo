package com.mewo.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Parcel
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mewo.R
import com.mewo.data.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val layout = ReminderNotificationLayout(context)
    private val greeting = WordEntity("hello", 0, "שלום", "Hello / peace", "shalom")
    private val friend =
        WordEntity(
            "friend",
            1,
            "חבר",
            "friend",
            "khaver",
            feminineHebrew = "חברה",
            feminineTransliteration = "khavera",
            feminineModification = "Add ה",
        )

    @Test fun compactLayoutKeepsOrderAndStylesAcrossParcelDelivery() {
        val view = render(parcelCopy(layout.compact(listOf(greeting))))
        assertEquals("שלום [shalom] Hello / peace", readable(view.text))
        assertStyles(view.text, "שלום", "[shalom]")
        assertEquals(2, view.maxLines)
    }

    @Test fun expandedLayoutKeepsMultipleWordsAndFeminineForms() {
        val view = render(parcelCopy(layout.expanded(listOf(greeting, friend))))
        assertEquals(
            "שלום [shalom] Hello / peace\n\nחבר [khaver] friend\nFeminine: חברה [khavera] friend (Add ה)",
            readable(view.text),
        )
        assertStyles(view.text, "שלום", "[shalom]")
        assertStyles(view.text, "חבר", "[khaver]")
        assertStyles(view.text, "חברה", "[khavera]")
    }

    @Test fun longPhrasesWrapAndTranslationRemainsRegular() {
        val long = greeting.copy(english = "A long translation that needs to wrap on a narrow notification")
        val view = render(layout.compact(listOf(long)))
        val width =
            android.view.View.MeasureSpec
                .makeMeasureSpec(240, android.view.View.MeasureSpec.EXACTLY)
        val height =
            android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        view.measure(width, height)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertTrue(view.layout.lineCount >= 2)
        assertStyles(view.text, "שלום", "[shalom]")
        assertEquals(Typeface.NORMAL, view.typeface.style)
    }

    @Test fun postedNotificationHasCustomViewsAndTapAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val notifications = ReminderNotifications(context)
        notifications.createChannel()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            assertFalse(notifications.publish(emptyList()))
            assertTrue(notifications.publish(listOf(greeting, friend)))
            val notification = manager.activeNotifications.single { it.id == 1 }.notification
            assertNotNull(notification.contentIntent)
            assertNotNull(notification.contentView)
            assertNotNull(notification.bigContentView)
            assertNotNull(notification.headsUpContentView)
            assertStyles(render(notification.contentView).text, "שלום", "[shalom]")
        } finally {
            // Opt in to keeping a preview for screenshots outside the test process.
            if (InstrumentationRegistry.getArguments().getString("notificationScreenshot") != "true") {
                manager.cancel(1)
            }
        }
    }

    private fun readable(text: CharSequence): String = text.toString().replace(Regex("[\u2066-\u2069]"), "")

    private fun render(views: RemoteViews): TextView =
        views.apply(context, FrameLayout(context)).findViewById(R.id.reminder_text)

    private fun parcelCopy(views: RemoteViews): RemoteViews {
        val parcel = Parcel.obtain()
        return try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            RemoteViews.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun assertStyles(
        text: CharSequence,
        hebrew: String,
        transcription: String,
    ) {
        val spanned = text as Spanned
        val spans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
        val ranges =
            spans.map {
                it.style to
                    spanned.subSequence(spanned.getSpanStart(it), spanned.getSpanEnd(it)).toString()
            }
        assertTrue(ranges.contains(Typeface.BOLD to hebrew))
        assertTrue(ranges.contains(Typeface.ITALIC to transcription))
        assertTrue(ranges.none { (_, content) -> content.contains("friend") || content.contains("Hello") })
    }
}
