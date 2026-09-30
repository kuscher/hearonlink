package io.github.kuscher.hearonlink.link

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.EarState
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.gestures.HeadSample
import io.github.kuscher.hearonlink.system.Notifications

/**
 * What HearOn Link does on its own while connected: pause when an AirPod comes out (and play again
 * only if we paused), lower media while you talk, warn once when a bud runs low.
 */
class Reactions(private val context: Context, private val prefs: Prefs, private val link: Link) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var earKnown = false
    private var pausedByUs = 0L
    private var duckedFrom = -1
    private var duckedTo = -1
    private val warned = HashSet<String>()

    fun reset() { earKnown = false; restoreVolume() }

    fun onEvent(e: AapEvent, before: PodState, after: PodState) {
        val s = prefs.settings.value
        when (e) {
            is AapEvent.Ear -> ear(before, after, s.earPause, s.earResume)
            is AapEvent.Conversation -> if (s.duckWhileTalking && after.conversationAwareness == true) talk(after.talking)
            is AapEvent.Battery -> if (s.lowBattery) battery(after)
            else -> {}
        }
    }

    private fun EarState.inEar() = this == EarState.IN_EAR

    private fun ear(before: PodState, after: PodState, pause: Boolean, resume: Boolean) {
        if (!earKnown) { earKnown = true; return } // the first report is just the current state
        val wasIn = listOf(before.left.ear, before.right.ear).count { it.inEar() }
        val nowIn = listOf(after.left.ear, after.right.ear).count { it.inEar() }
        if (nowIn < wasIn && pause && audio.isMusicActive) {
            media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            pausedByUs = SystemClock.elapsedRealtime()
        } else if (nowIn > wasIn && nowIn >= wasIn && pausedByUs > 0 && resume) {
            // Play again only if we paused it, and not after a long break.
            if (SystemClock.elapsedRealtime() - pausedByUs < 10 * 60_000 && !audio.isMusicActive) media(KeyEvent.KEYCODE_MEDIA_PLAY)
            pausedByUs = 0
        }
    }

    private fun media(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun talk(talking: Boolean) {
        if (talking && duckedFrom < 0 && audio.isMusicActive) {
            val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val low = maxOf(1, (v * 0.35f).toInt())
            if (low < v) runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, low, 0)
                duckedFrom = v; duckedTo = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        } else if (!talking) restoreVolume()
    }

    private fun restoreVolume() {
        if (duckedFrom < 0) return
        // Leave it alone if you changed the volume yourself meanwhile.
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == duckedTo) runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, duckedFrom, 0)
        }
        duckedFrom = -1; duckedTo = -1
    }

    private fun battery(p: PodState) {
        for ((key, level) in listOf("left" to p.left.battery, "right" to p.right.battery, "single" to p.single)) {
            if (level == null) continue
            if (level.charging || level.percent > 15) { warned -= key; continue }
            if (level.percent <= 10 && warned.add(key)) Notifications.lowBattery(context, key, level.percent, p)
        }
    }
}

/**
 * Head motion from the AirPods' motion sensor stream. The two values used for gestures are the
 * int16 fields at payload offsets 30 (up/down) and 28 (sideways), as mapped in public protocol
 * notes; `./hol debug head` records raw frames to check them on new firmware.
 */
object HeadMotion {
    var verticalOffset = 30
    var horizontalOffset = 28

    fun decode(p: ByteArray): HeadSample? {
        val need = maxOf(verticalOffset, horizontalOffset) + 2
        if (p.size < need) return null
        fun s16(i: Int) = ((p[i].toInt() and 0xff) or (p[i + 1].toInt() shl 8)).toShort().toFloat()
        return HeadSample(SystemClock.elapsedRealtime(), s16(verticalOffset), s16(horizontalOffset))
    }
}
