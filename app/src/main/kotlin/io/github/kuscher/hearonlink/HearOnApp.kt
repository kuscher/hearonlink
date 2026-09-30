package io.github.kuscher.hearonlink

import android.app.Application
import android.content.Context
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.link.Link
import io.github.kuscher.hearonlink.system.Notifications

class HearOnApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var link: Link; private set
    lateinit var controls: io.github.kuscher.hearonlink.link.Controls; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        link = Link(this, prefs)
        controls = io.github.kuscher.hearonlink.link.Controls(this, prefs, link).also { it.start() }
        Notifications.createChannels(this)
    }
}

val Context.hearOn: HearOnApp get() = applicationContext as HearOnApp
