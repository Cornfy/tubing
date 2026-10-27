package io.nekohasekai.sfa.database

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TemplateProfileSourceTest {
    @Test
    fun emptyOverridePreservesOriginalSource() {
        val original = """{"_extra":{"sub_urls":[["Original","https://example.invalid/sub"]],"regions":[]}}"""
        assertEquals(original, TemplateProfileSource.overrideSubscriptions(original, listOf("", "  ")))
    }

    @Test
    fun overridePreservesOtherTemplateFields() {
        val result = JSONObject(TemplateProfileSource.overrideSubscriptions(
            """{"_extra":{"regions":["test"],"sub_urls":[]},"outbounds":[]}""",
            listOf(" https://example.invalid/a?token=fake ", "https://example.invalid/b"),
        ))
        val extra = result.getJSONObject("_extra")
        assertEquals("test", extra.getJSONArray("regions").getString(0))
        assertEquals(2, extra.getJSONArray("sub_urls").length())
        assertTrue(result.has("outbounds"))
        assertTrue(TemplateProfileSource.hasSubscriptions(result.toString()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidEnteredURLIsNotSilentlyDiscarded() {
        TemplateProfileSource.overrideSubscriptions("{}", listOf("https://example.invalid/a", "not-a-url"))
    }

    @Test
    fun noUsableSubscriptionsRemainDrafts() {
        assertFalse(TemplateProfileSource.hasSubscriptions("{}"))
        assertFalse(TemplateProfileSource.hasSubscriptions("""{"_extra":{"sub_urls":[["name",""],"https://"]}}"""))
        assertFalse(TemplateProfileSource.hasSubscriptions("""{"_extra":{"sub_urls":["https://example.invalid/a"]}}"""))
        assertTrue(TemplateProfileSource.hasSubscriptions("""{"_extra":{"sub_urls":[["name","https://example.invalid/a"]]}}"""))
    }

    @Test(expected = org.json.JSONException::class)
    fun invalidTemplateIsNotSilentlyReplacedDuringOverride() {
        TemplateProfileSource.overrideSubscriptions("not-json", listOf("https://example.invalid/a"))
    }
}
