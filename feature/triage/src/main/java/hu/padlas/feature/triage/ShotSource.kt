package hu.padlas.feature.triage

/**
 * The kind of app a screenshot was taken in. Android writes the app's name into the file
 * name (Samsung: `Screenshot_20261030_191834_Waze.jpg`, Pixel:
 * `Screenshot_20231010-101010_Chrome.png`), and that says more about a screenshot than its
 * text does, before any recognition has run. Only kinds that are almost always a passing
 * glance get groups of their own; a chat, a bank or a browser page still goes by its text.
 */
enum class ShotSource(
    val title: String,
    /** The group that collects the apps with only a few shots each. */
    val othersTitle: String,
    val hint: String,
    /** Worth a look before it goes: arrives kept, "Mind törlése" marks it. */
    val startsKept: Boolean,
) {
    NAVIGATION(
        title = "Navigáció",
        othersTitle = "Más navigációs appok",
        hint = "Útvonalak, érkezési idők, forgalmi riasztások. Utólag szinte sosem kellenek.",
        startsKept = false,
    ),
    HOME(
        title = "Kezdőképernyő és rendszer",
        othersTitle = "Más rendszerappok",
        hint = "Kezdőképernyő, óra, ébresztő, számológép, áruház. Többnyire véletlen vagy egyszeri képek.",
        startsKept = false,
    ),
    GAME(
        title = "Játék",
        othersTitle = "Más játékok",
        hint = "Eredmények és pillanatképek játékokból.",
        startsKept = false,
    ),
    MEDIA(
        title = "Videó és zene",
        othersTitle = "Más videó- és zeneappok",
        hint = "Videók és dalok, amiknek a címe kellett. Nincsenek kijelölve: a „Mind törlése” jelöli ki őket.",
        startsKept = true,
    ),
    SOCIAL(
        title = "Közösségi média",
        othersTitle = "Más közösségi appok",
        hint = "Posztok, sztorik, mémek. Lehet köztük emlék, ezért nincsenek kijelölve: a „Mind törlése” jelöli ki őket.",
        startsKept = true,
    ),
}

/** One shot with the app it came from, as [ShotSources.appOf] and [ShotSources.sourceOf] read it. */
internal data class SourcedShot<T>(val item: T, val app: String, val source: ShotSource, val bytes: Long)

/** A Kidobó group built from where its shots came from: one app, or the small ones of a kind. */
internal data class SourceGroup<T>(
    val key: String,
    val source: ShotSource,
    val title: String,
    val apps: List<String>,
    val items: List<T>,
)

internal object ShotSources {

    /** Below this many shots an app shares one group with the other small ones of its kind. */
    const val MIN_APP_GROUP = 6

    private val NAME = Regex("^Screenshot_\\d{8}[-_]\\d{6}(?:_(.+))?\\.[A-Za-z0-9]+$")

    /** A copy the gallery made: `Waze (1)`, `Waze(2)`. */
    private val COPY_SUFFIX = Regex("\\s*\\(\\d+\\)$")

    /** The app's name as the screenshot file carries it, or null for any other name. */
    fun appOf(displayName: String): String? =
        NAME.matchEntire(displayName)?.groupValues?.get(1)
            ?.replace(COPY_SUFFIX, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun normalize(label: String): String = deaccent(label.lowercase()).trim()

    /**
     * The dictionary first, then what the installed app says about itself (a game flag, a
     * maps or social category), keyed by [normalize]d label. An app that wrote a chat or a
     * bank screen is deliberately not here: those go by their text.
     */
    fun sourceOf(app: String, installed: Map<String, ShotSource> = emptyMap()): ShotSource? {
        val key = normalize(app)
        if (key in BY_TEXT) return null
        KNOWN[key]?.let { return it }
        if (ALARM.containsMatchIn(key)) return ShotSource.HOME
        return installed[key]
    }

    /**
     * Apps with enough shots get a group each, the rest of a kind share one. Kinds come in
     * [ShotSource] order, the biggest apps first and the shared group last within a kind.
     */
    fun <T> group(shots: List<SourcedShot<T>>, minPerApp: Int = MIN_APP_GROUP): List<SourceGroup<T>> =
        shots.groupBy { it.source }.toSortedMap().flatMap { (source, ofSource) ->
            val (big, small) = ofSource.groupBy { it.app }.entries.partition { it.value.size >= minPerApp }
            val single = big
                .sortedByDescending { entry -> entry.value.sumOf { it.bytes } }
                .map { (app, appShots) ->
                    SourceGroup("app:${normalize(app)}", source, app, listOf(app), appShots.map { it.item })
                }
            val shared = if (small.isEmpty()) {
                emptyList()
            } else {
                val apps = small.sortedByDescending { it.value.size }.map { it.key }
                listOf(
                    SourceGroup(
                        key = "apps:${source.name}",
                        source = source,
                        title = if (single.isEmpty()) source.title else source.othersTitle,
                        apps = apps,
                        items = small.flatMap { entry -> entry.value.map { it.item } },
                    ),
                )
            }
            single + shared
        }

    private val ALARM = Regex("\\b(alarm|ebreszto)")

    /** Chats, mail, banks, browsers, notes: the picture is the content, the text decides. */
    private val BY_TEXT = setOf(
        "messenger", "whatsapp", "viber", "telegram", "signal", "discord", "teams", "microsoft teams",
        "messages", "uzenetek", "gmail", "outlook", "skype", "slack", "zoom",
        "chrome", "samsung internet", "firefox", "opera", "edge", "brave",
        "otp bank", "revolut", "k&h", "cib", "erste", "mbh bank", "raiffeisen", "granit", "wise", "paypal",
        "settings", "beallitasok", "samsung notes", "keep notes", "notes", "jegyzetek",
        "gallery", "galeria", "google photos", "fotok", "photos",
    )

    private val KNOWN: Map<String, ShotSource> = buildMap {
        listOf(
            "waze", "maps", "google maps", "terkep", "terkepek", "here wego", "citymapper", "moovit",
            "bkk futar", "budapestgo", "mav", "bolt", "uber", "freenow", "free now", "organic maps",
            "osmand", "sygic", "mapy.cz", "petal maps", "yandex maps", "yandex navi",
        ).forEach { put(it, ShotSource.NAVIGATION) }
        listOf(
            "one ui home", "launcher", "nova launcher", "clock", "ora", "calculator", "szamologep",
            "google play store", "play store", "galaxy store", "system ui", "weather", "idojaras",
            "device care", "eszkozapolas", "battery", "akkumulator",
        ).forEach { put(it, ShotSource.HOME) }
        listOf(
            "youtube", "yt studio", "youtube music", "spotify", "shazam", "genius", "netflix",
            "hbo max", "max", "disney+", "twitch", "deezer", "soundcloud", "tidal", "prime video",
            "skyshowtime", "rtl+", "plex", "apple music", "musixmatch", "capcut",
        ).forEach { put(it, ShotSource.MEDIA) }
        listOf(
            "instagram", "facebook", "reddit", "snapchat", "tiktok", "pinterest", "bereal", "bereal.",
            "twitter", "x", "threads", "quora", "tumblr", "9gag", "linkedin", "tinder", "bumble",
            "hinge", "imgur", "vk",
        ).forEach { put(it, ShotSource.SOCIAL) }
    }
}
