package eu.example.constructionlapse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wake an EXISTING foreground camera service. Never silently create one from a
 * background broadcast; Android 14+ while-in-use CAMERA restrictions prohibit it. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CaptureService.ACTION_SCHEDULE_ALARM) {
            CaptureService.activeService?.onScheduledWake()
        }
    }
}
