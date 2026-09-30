package io.github.kuscher.hearonlink.aap

/** What the app may show for a family of AirPods. The AirPods' own reports refine it at runtime. */
enum class Feature {
    LISTENING_MODES, ADAPTIVE, CONVERSATION_AWARENESS, PERSONALIZED_VOLUME, HEAD_GESTURES,
    VOLUME_SWIPE, STEM, ONE_BUD_ANC, HEARING_PROTECTION, EAR_DETECTION,
}

enum class Family(val displayName: String, val features: Set<Feature>) {
    AIRPODS_1("AirPods", setOf(Feature.STEM, Feature.EAR_DETECTION)),
    AIRPODS_2("AirPods (2nd gen)", setOf(Feature.STEM, Feature.EAR_DETECTION)),
    AIRPODS_3("AirPods (3rd gen)", setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.HEAD_GESTURES)),
    AIRPODS_4("AirPods 4", setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.HEAD_GESTURES, Feature.PERSONALIZED_VOLUME)),
    AIRPODS_4_ANC(
        "AirPods 4 with ANC",
        setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.HEAD_GESTURES, Feature.PERSONALIZED_VOLUME,
            Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS),
    ),
    PRO("AirPods Pro", setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.LISTENING_MODES, Feature.ONE_BUD_ANC)),
    PRO_2(
        "AirPods Pro 2",
        setOf(Feature.STEM, Feature.EAR_DETECTION, Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS,
            Feature.PERSONALIZED_VOLUME, Feature.HEAD_GESTURES, Feature.VOLUME_SWIPE, Feature.ONE_BUD_ANC),
    ),
    PRO_2_USB_C("AirPods Pro 2 (USB-C)", PRO_2.features),
    PRO_3("AirPods Pro 3", PRO_2.features + Feature.HEARING_PROTECTION),
    MAX("AirPods Max", setOf(Feature.LISTENING_MODES)),
    MAX_USB_C("AirPods Max (USB-C)", setOf(Feature.LISTENING_MODES)),
    MAX_2(
        "AirPods Max 2",
        setOf(Feature.LISTENING_MODES, Feature.ADAPTIVE, Feature.CONVERSATION_AWARENESS, Feature.PERSONALIZED_VOLUME),
    ),
    AIRPODS_5("AirPods 5", AIRPODS_4.features),
    UNKNOWN("AirPods", setOf(Feature.EAR_DETECTION)),
    ;

    val isHeadphones get() = this == MAX || this == MAX_USB_C || this == MAX_2

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
        )

        fun ofModelNumber(n: String?): Family = n?.let { byModelNumber[it.trim().uppercase()] } ?: UNKNOWN
        fun ofProductId(id: Int): Family = byProductId[id] ?: UNKNOWN
    }
}
