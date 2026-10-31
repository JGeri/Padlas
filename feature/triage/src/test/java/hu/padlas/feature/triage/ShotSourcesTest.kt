package hu.padlas.feature.triage

import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShotSourcesTest {

    @Test
    fun `samsung and pixel names carry the app`() {
        assertEquals("Waze", ShotSources.appOf("Screenshot_20261030_191834_Waze.jpg"))
        assertEquals("Waze", ShotSources.appOf("Screenshot_20220910-154403_Waze.jpg"))
        assertEquals("I Can't Wake Up! Alarm Clock", ShotSources.appOf("Screenshot_20240101_070000_I Can't Wake Up! Alarm Clock.jpg"))
        assertEquals("One UI Home", ShotSources.appOf("Screenshot_20250101_120000_One UI Home.png"))
    }

    @Test
    fun `a gallery copy keeps the app name`() {
        assertEquals("Waze", ShotSources.appOf("Screenshot_20261030_191834_Waze (1).jpg"))
        assertEquals("Waze", ShotSources.appOf("Screenshot_20261030_191834_Waze(2).jpg"))
    }

    @Test
    fun `names without an app are not guessed`() {
        assertNull(ShotSources.appOf("Screenshot_20261030_191834.jpg"))
        assertNull(ShotSources.appOf("IMG_20261030_191834.jpg"))
        assertNull(ShotSources.appOf("Screenshot_Waze.jpg"))
    }

    @Test
    fun `known apps land in their kind and chats stay with the text`() {
        assertEquals(ShotSource.NAVIGATION, ShotSources.sourceOf("Waze"))
        assertEquals(ShotSource.NAVIGATION, ShotSources.sourceOf("Maps"))
        assertEquals(ShotSource.HOME, ShotSources.sourceOf("One UI Home"))
        assertEquals(ShotSource.HOME, ShotSources.sourceOf("I Can't Wake Up! Alarm Clock"))
        assertEquals(ShotSource.HOME, ShotSources.sourceOf("Számológép"))
        assertEquals(ShotSource.MEDIA, ShotSources.sourceOf("YouTube"))
        assertEquals(ShotSource.SOCIAL, ShotSources.sourceOf("Instagram"))
        assertNull(ShotSources.sourceOf("Messenger"))
        assertNull(ShotSources.sourceOf("OTP Bank"))
        assertNull(ShotSources.sourceOf("Chrome"))
        assertNull(ShotSources.sourceOf("Settings"))
    }

    @Test
    fun `an installed app's own category fills the gaps, but never over a chat`() {
        val installed = mapOf(
            "jurassic world" to ShotSource.GAME,
            "messenger" to ShotSource.SOCIAL,
        )
        assertEquals(ShotSource.GAME, ShotSources.sourceOf("Jurassic World", installed))
        assertNull(ShotSources.sourceOf("Messenger", installed))
        assertNull(ShotSources.sourceOf("Moodle", installed))
    }

    @Test
    fun `big apps get their own group, small ones share one per kind`() {
        val shots = List(10) { shot("Waze", ShotSource.NAVIGATION, 10) } +
            List(7) { shot("Maps", ShotSource.NAVIGATION, 30) } +
            List(2) { shot("Bolt", ShotSource.NAVIGATION, 1) } +
            List(3) { shot("Uber", ShotSource.NAVIGATION, 1) } +
            List(8) { shot("Instagram", ShotSource.SOCIAL, 1) }

        val groups = ShotSources.group(shots)

        assertEquals(
            listOf(
                UiText.Raw("Maps"),
                UiText.Raw("Waze"),
                uiText(R.string.triage_source_navigation_others),
                UiText.Raw("Instagram"),
            ),
            groups.map { it.title },
        )
        assertEquals(listOf("Uber", "Bolt"), groups[2].apps)
        assertEquals(5, groups[2].items.size)
        assertEquals(groups.size, groups.map { it.key }.toSet().size)
    }

    @Test
    fun `a kind with only small apps is titled by the kind`() {
        val groups = ShotSources.group(List(3) { shot("Pottery 2", ShotSource.GAME, 1) })

        assertEquals(listOf(uiText(R.string.triage_source_game_title)), groups.map { it.title })
    }

    private fun shot(app: String, source: ShotSource, bytes: Long) = SourcedShot(app, app, source, bytes)
}
