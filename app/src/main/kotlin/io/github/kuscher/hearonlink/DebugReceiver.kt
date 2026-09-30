package io.github.kuscher.hearonlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.hex
import io.github.kuscher.hearonlink.aap.hexBytes
import io.github.kuscher.hearonlink.link.HeadMotion
import io.github.kuscher.hearonlink.link.Link
import io.github.kuscher.hearonlink.link.LinkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * adb test hooks (`./hol debug …`). Only the shell can send these (the receiver needs DUMP).
 * Answers go to logcat as "debug CMD -> result". Never logs serial numbers or keys.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = (intent.getStringExtra("c") ?: return).trim().split(Regex("\\s+"))
        val app = context.hearOn
        val link = app.link
        val cmd = parts[0]
        fun say(s: String) = Log.i(Link.TAG, "debug $cmd -> $s")
        when (cmd) {
            "state" -> {
                val s = link.state.value; val p = s.pod
                say("status=${s.status} connected=${s.connected} name=${s.name} model=${p.info?.modelNumber} fw=${p.info?.firmware} build=${p.info?.build} " +
                    "mode=${p.listeningMode} L=${p.left} R=${p.right} case=${p.case} leftPrimary=${p.leftPrimary} " +
                    "controls=${p.controls.entries.sortedBy { it.key }.joinToString(",") { "%02x:%s".format(it.key, it.value) }} " +
                    "keys=${app.prefs.irk != null}/${app.prefs.encKey != null} last=${s.last.at} msg=${s.message}")
            }
            "connect" -> { LinkService.start(context); say("ok") }
            "session" -> {
                // Run the link without the service for a few seconds (the service needs a companion
                // association or a foreground app to start), then report.
                val secs = (parts.getOrNull(1)?.toLongOrNull() ?: 6).coerceIn(2, 9)
                val pending = goAsync()
                scope.launch {
                    link.connect()
                    delay(secs * 1000)
                    val st = link.state.value; val p = st.pod
                    say("status=${st.status} handshake=${p.handshakeDone} name=${p.info?.name} model=${p.info?.modelNumber} family=${p.family} " +
                        "mode=${p.listeningMode} L=${p.left} R=${p.right} case=${p.case} ca=${p.conversationAwareness} pv=${p.personalizedVolume} " +
                        "adaptive=${p.adaptiveLevel} cycle=${p.cycle} keys=${app.prefs.irk != null}/${app.prefs.encKey != null} msg=${st.message}")
                    if (parts.getOrNull(2) != "keep") link.disconnect()
                    pending.finish()
                }
            }
            "disconnect" -> { link.disconnect(); say("ok") }
            "send" -> say(if (link.sendRaw(parts.drop(1).joinToString("").hexBytes())) "sent" else "no session")
            "mode" -> { ListeningMode.entries.firstOrNull { it.name.startsWith(parts[1].uppercase()) }?.let(link::setMode); say("ok") }
            "control" -> { link.setControl(parts[1].toInt(16), *parts.drop(2).map { it.toInt() }.toIntArray()); say("ok") }
            "log" -> { Link.DEBUG = parts.getOrNull(1) != "off"; say("${Link.DEBUG}") }
            "onboarded" -> { app.prefs.update { it.copy(onboarded = parts.getOrNull(1) != "false") }; say("ok") }
            "offsets" -> { HeadMotion.verticalOffset = parts[1].toInt(); HeadMotion.horizontalOffset = parts[2].toInt(); say("ok") }
            "head" -> {
                // Record N seconds of raw sensor frames to cache/head.txt ("ms hex" per line).
                val secs = parts.getOrNull(1)?.toLongOrNull() ?: 8
                val pending = goAsync()
                scope.launch {
                    val f = File(context.cacheDir, "head.txt")
                    val out = StringBuilder()
                    val t0 = System.currentTimeMillis()
                    link.trackHead("debug", true)
                    withTimeoutOrNull(secs * 1000) {
                        link.events.collect { e -> if (e is AapEvent.Sensor) out.append(System.currentTimeMillis() - t0).append(' ').append(e.payload.hex()).append('\n') }
                    }
                    link.trackHead("debug", false)
                    f.writeText(out.toString())
                    say("${out.lines().size - 1} frames -> ${f.path}")
                    pending.finish()
                }
            }
            "render" -> {
                // render KIND W H DPI DARK(0|1) -> cache/render.png (see ui/Shots.kt)
                val pending = goAsync()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    io.github.kuscher.hearonlink.ui.Shots.render(context, parts[1], parts[2].toInt(), parts[3].toInt(), parts[4].toInt(), parts.getOrNull(5) == "1",
                        File(context.cacheDir, "render.png")) { say(it); pending.finish() }
                }
            }
            "selftest" -> {
                // One receiver call: connect, flip a control id (default 0x28, conversation awareness)
                // to value A and back to value B, and report what the AirPods echoed.
                val id = parts.getOrNull(1)?.toInt(16) ?: 0x28
                val a = parts.getOrNull(2)?.toInt() ?: 1
                val b = parts.getOrNull(3)?.toInt() ?: 2
                val pending = goAsync()
                scope.launch {
                    val seen = StringBuilder()
                    val watch = launch { link.events.collect { e -> if (e is AapEvent.Control && e.id == id) seen.append(" echo=").append(e.v1) } }
                    link.connect()
                    withTimeoutOrNull(5000) { while (!link.state.value.connected) delay(100) }
                    delay(1500)
                    val before = link.state.value.pod.control(id)
                    link.setControl(id, a); delay(1500)
                    link.setControl(id, b); delay(1500)
                    watch.cancel()
                    say("id=%02x before=$before set $a then $b ->$seen".format(id))
                    link.disconnect()
                    pending.finish()
                }
            }
            "wait" -> { // wait until connected (max N s), for scripts
                val pending = goAsync()
                scope.launch {
                    val secs = parts.getOrNull(1)?.toIntOrNull() ?: 10
                    var n = 0
                    while (!link.state.value.connected && n < secs * 10) { delay(100); n++ }
                    say("${link.state.value.connected}")
                    pending.finish()
                }
            }
            else -> say("unknown")
        }
    }

    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
