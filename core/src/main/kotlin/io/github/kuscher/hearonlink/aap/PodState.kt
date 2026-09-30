package io.github.kuscher.hearonlink.aap

data class Level(val percent: Int, val charging: Boolean)

data class Bud(val battery: Level? = null, val ear: EarState = EarState.UNKNOWN)

/** The AirPods' state as HearOn Link knows it. Immutable; [reduce] folds in one event. */
data class PodState(
    val handshakeDone: Boolean = false,
    val info: DeviceInfo? = null,
    val left: Bud = Bud(),
    val right: Bud = Bud(),
    val single: Level? = null,
    val case: Level? = null,
    val leftPrimary: Boolean = true,
    /** Latest value bytes per control id, as the AirPods reported them. */
    val controls: Map<Int, List<Int>> = emptyMap(),
    val talking: Boolean = false,
    val irk: ByteArray? = null,
    val encKey: ByteArray? = null,
) {
    val family: Family get() = Family.ofModelNumber(info?.modelNumber)
    val displayName: String get() = info?.name ?: family.displayName

    fun control(id: Int): Int? = controls[id]?.firstOrNull()
    fun flag(id: Int): Boolean? = control(id)?.let { it == Control.ON }

    val listeningMode: ListeningMode? get() = control(Control.LISTENING_MODE)?.let(ListeningMode::of)
    val cycle: Set<ListeningMode>? get() = control(Control.LISTENING_CYCLE)?.let(ListeningMode::fromCycleMask)
    val conversationAwareness: Boolean? get() = flag(Control.CONVERSATION_AWARENESS)
    val personalizedVolume: Boolean? get() = flag(Control.PERSONALIZED_VOLUME)
    val adaptiveLevel: Int? get() = control(Control.ADAPTIVE_LEVEL)
    val oneBudAnc: Boolean? get() = flag(Control.ONE_BUD_ANC)
    val volumeSwipe: Boolean? get() = flag(Control.VOLUME_SWIPE)
    val toneVolume: Int? get() = control(Control.TONE_VOLUME)

    /** A feature shows when the family has it; a reported control state confirms it too. */
    fun has(f: Feature): Boolean {
        val reported = when (f) {
            Feature.CONVERSATION_AWARENESS -> Control.CONVERSATION_AWARENESS
            Feature.PERSONALIZED_VOLUME -> Control.PERSONALIZED_VOLUME
            Feature.ADAPTIVE -> Control.ADAPTIVE_LEVEL
            Feature.VOLUME_SWIPE -> Control.VOLUME_SWIPE
            Feature.ONE_BUD_ANC -> Control.ONE_BUD_ANC
            Feature.LISTENING_MODES -> Control.LISTENING_MODE
            Feature.HEARING_PROTECTION -> Control.HEARING_PROTECTION
            else -> null
        }
        return f in family.features || (reported != null && controls.containsKey(reported))
    }

    fun reduce(e: AapEvent): PodState = when (e) {
        AapEvent.HandshakeAck -> copy(handshakeDone = true)
        is AapEvent.Info -> copy(info = e.info)
        is AapEvent.BudRole -> copy(leftPrimary = e.leftPrimary)
        is AapEvent.Battery -> {
            var s = this
            for (r in e.readings) {
                val lv = r.level?.let { Level(it, r.charging) }
                s = when (r.component) {
                    Component.LEFT -> s.copy(left = s.left.copy(battery = lv))
                    Component.RIGHT -> s.copy(right = s.right.copy(battery = lv))
                    Component.CASE -> s.copy(case = lv)
                    Component.SINGLE -> s.copy(single = lv)
                }
            }
            s
        }
        is AapEvent.Ear -> {
            val (l, r) = if (leftPrimary) e.primary to e.secondary else e.secondary to e.primary
            copy(left = left.copy(ear = l), right = right.copy(ear = r))
        }
        is AapEvent.Control -> copy(controls = controls + (e.id to e.value.map { it.toInt() and 0xff }))
        is AapEvent.Conversation -> copy(talking = e.level in 1..5)
        is AapEvent.Keys -> copy(irk = e.irk ?: irk, encKey = e.encKey ?: encKey)
        else -> this
    }

    /** The next mode a press-and-hold would pick, in the order Off → Transparency → Adaptive → ANC. */
    fun nextMode(): ListeningMode {
        val allowed = (cycle ?: setOf(ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION))
            .ifEmpty { setOf(ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION) }
        val order = ListeningMode.DISPLAY_ORDER.filter { it in allowed }
        val i = order.indexOf(listeningMode)
        return order[(i + 1) % order.size]
    }

    override fun equals(other: Any?): Boolean = other is PodState && handshakeDone == other.handshakeDone &&
        info == other.info && left == other.left && right == other.right && single == other.single &&
        case == other.case && leftPrimary == other.leftPrimary && controls == other.controls &&
        talking == other.talking && irk.contentEqualsNullable(other.irk) && encKey.contentEqualsNullable(other.encKey)

    override fun hashCode(): Int = listOf(handshakeDone, info, left, right, single, case, leftPrimary, controls, talking).hashCode()
}

private fun ByteArray?.contentEqualsNullable(o: ByteArray?) = if (this == null) o == null else o != null && contentEquals(o)
