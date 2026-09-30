package io.github.kuscher.hearonlink.aap

/** What the app may show for a family of AirPods. The AirPods' own reports refine it at runtime. */
enum class Feature {
    LISTENING_MODES, ADAPTIVE, CONVERSATION_AWARENESS, PERSONALIZED_VOLUME, HEAD_GESTURES,
    VOLUME_SWIPE, STEM, ONE_BUD_ANC, HEARING_PROTECTION, EAR_DETECTION,
    CASE_SOUNDS, SLEEP_DETECTION, HEART_RATE, CROWN, OPTIMIZED_CHARGING, CALL_CONTROLS,
}

private val BUDS = setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.CALL_CONTROLS, Feature.OPTIMIZED_CHARGING)

enum class Family(val displayName: String, val features: Set<Feature>, val beats: Boolean = false) {
    AIRPODS_1("AirPods", BUDS - Feature.OPTIMIZED_CHARGING),
    AIRPODS_2("AirPods (2nd gen)", BUDS - Feature.OPTIMIZED_CHARGING),
    AIRPODS_3("AirPods (3rd gen)", BUDS + Feature.HEAD_GESTURES),
    AIRPODS_4("AirPods 4", BUDS + setOf(Feature.HEAD_GESTURES, Feature.PERSONALIZED_VOLUME, Feature.SLEEP_DETECTION)),
    AIRPODS_4_ANC(
        "AirPods 4 with ANC",
        BUDS + setOf(Feature.HEAD_GESTURES, Feature.PERSONALIZED_VOLUME, Feature.SLEEP_DETECTION, Feature.CASE_SOUNDS,
            Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS),
    ),
    PRO("AirPods Pro", BUDS + setOf(Feature.LISTENING_MODES, Feature.ONE_BUD_ANC)),
    PRO_2(
        "AirPods Pro 2",
        BUDS + setOf(Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS, Feature.PERSONALIZED_VOLUME,
            Feature.HEAD_GESTURES, Feature.VOLUME_SWIPE, Feature.ONE_BUD_ANC, Feature.SLEEP_DETECTION, Feature.CASE_SOUNDS),
    ),
    PRO_2_USB_C("AirPods Pro 2 (USB-C)", PRO_2.features),
    PRO_3("AirPods Pro 3", PRO_2.features + setOf(Feature.HEARING_PROTECTION, Feature.HEART_RATE)),
    MAX("AirPods Max", setOf(Feature.LISTENING_MODES, Feature.CROWN)),
    MAX_USB_C("AirPods Max (USB-C)", setOf(Feature.LISTENING_MODES, Feature.CROWN)),
    MAX_2(
        "AirPods Max 2",
        setOf(Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS, Feature.PERSONALIZED_VOLUME, Feature.CROWN),
    ),
    AIRPODS_5("AirPods 5", AIRPODS_4.features),

    // Beats with Apple chips speak the same protocol; what they support shows up in their reports.
    POWERBEATS_3("Powerbeats3", emptySet(), beats = true),
    POWERBEATS_4("Powerbeats", emptySet(), beats = true),
    POWERBEATS_PRO("Powerbeats Pro", setOf(Feature.EAR_DETECTION), beats = true),
    POWERBEATS_PRO_2("Powerbeats Pro 2", setOf(Feature.EAR_DETECTION, Feature.LISTENING_MODES, Feature.HEART_RATE), beats = true),
    POWERBEATS_FIT("Powerbeats Fit", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_X("BeatsX", emptySet(), beats = true),
    BEATS_FLEX("Beats Flex", emptySet(), beats = true),
    BEATS_SOLO_3("Beats Solo3", emptySet(), beats = true),
    BEATS_SOLO_4("Beats Solo 4", emptySet(), beats = true),
    BEATS_SOLO_PRO("Beats Solo Pro", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_SOLO_BUDS("Beats Solo Buds", emptySet(), beats = true),
    BEATS_STUDIO_3("Beats Studio3", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_STUDIO_PRO("Beats Studio Pro", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_STUDIO_BUDS("Beats Studio Buds", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_STUDIO_BUDS_PLUS("Beats Studio Buds +", setOf(Feature.LISTENING_MODES), beats = true),
    BEATS_FIT_PRO("Beats Fit Pro", setOf(Feature.LISTENING_MODES, Feature.EAR_DETECTION), beats = true),

    UNKNOWN("Headphones", setOf(Feature.EAR_DETECTION)),
    ;

    val isHeadphones get() = this in setOf(MAX, MAX_USB_C, MAX_2, BEATS_SOLO_3, BEATS_SOLO_4, BEATS_SOLO_PRO, BEATS_STUDIO_3, BEATS_STUDIO_PRO)

    companion object {
        private val byModelNumber = buildMap {
            fun put(f: Family, vararg n: String) = n.forEach { put(it, f) }
            put(AIRPODS_1, "A1523", "A1722")
            put(AIRPODS_2, "A2032", "A2031")
            put(AIRPODS_3, "A2565", "A2564")
            put(AIRPODS_4, "A3053", "A3050", "A3054")
            put(AIRPODS_4_ANC, "A3056", "A3055", "A3057")
            put(PRO, "A2084", "A2083")
            put(PRO_2, "A2931", "A2699", "A2698")
            put(PRO_2_USB_C, "A3047", "A3048", "A3049")
            put(PRO_3, "A3063", "A3064", "A3065")
            put(MAX, "A2096")
            put(MAX_USB_C, "A3184")
            put(MAX_2, "A3454")
            put(AIRPODS_5, "A3531", "A3532", "A3533", "A3439", "A3440", "A3441")
        }

        /** Product ids from the BLE proximity advert (little-endian bytes 3–4). */
        private val byProductId = mapOf(
            0x2002 to AIRPODS_1, 0x200F to AIRPODS_2, 0x2013 to AIRPODS_3, 0x2019 to AIRPODS_4,
            0x201B to AIRPODS_4_ANC, 0x200E to PRO, 0x2014 to PRO_2, 0x2024 to PRO_2_USB_C, 0x2027 to PRO_3,
            0x200A to MAX, 0x201F to MAX_USB_C, 0x202D to MAX_2, 0x2036 to AIRPODS_5, 0x2030 to AIRPODS_5,
            0x2003 to POWERBEATS_3, 0x200B to POWERBEATS_PRO, 0x200D to POWERBEATS_4, 0x201D to POWERBEATS_PRO_2,
            0x202F to POWERBEATS_FIT, 0x2005 to BEATS_X, 0x2006 to BEATS_SOLO_3, 0x2009 to BEATS_STUDIO_3,
            0x200C to BEATS_SOLO_PRO, 0x2010 to BEATS_FLEX, 0x2011 to BEATS_STUDIO_BUDS, 0x2012 to BEATS_FIT_PRO,
            0x2016 to BEATS_STUDIO_BUDS_PLUS, 0x2017 to BEATS_STUDIO_PRO, 0x2025 to BEATS_SOLO_4, 0x2026 to BEATS_SOLO_BUDS,
        )

        fun ofModelNumber(n: String?): Family = n?.let { byModelNumber[it.trim().uppercase()] } ?: UNKNOWN
        fun ofProductId(id: Int): Family = byProductId[id] ?: UNKNOWN
    }
}
