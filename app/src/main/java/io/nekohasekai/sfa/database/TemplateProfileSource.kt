package io.nekohasekai.sfa.database

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object TemplateProfileSource {
    fun isSubscriptionURL(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    fun overrideSubscriptions(content: String, enteredURLs: List<String>): String {
        val urls = enteredURLs.map { it.trim() }.filter { it.isNotEmpty() }
        require(urls.all(::isSubscriptionURL)) { "Invalid subscription URL" }
        // 未填写覆盖地址时保留原模板，包括其中的订阅名称和额外字段。
        if (urls.isEmpty()) return content
        val template = JSONObject(content.ifBlank { "{}" })
        val extra = if (template.has("_extra")) template.getJSONObject("_extra") else JSONObject()
        val subscriptions = JSONArray()
        urls.forEachIndexed { index, url -> subscriptions.put(JSONArray().put("Sub ${index + 1}").put(url)) }
        extra.put("sub_urls", subscriptions)
        template.put("_extra", extra)
        return template.toString(2)
    }

    fun hasSubscriptions(content: String): Boolean {
        val template = JSONObject(content.ifBlank { "{}" })
        val urls = template.optJSONObject("_extra")?.optJSONArray("sub_urls") ?: return false
        return (0 until urls.length()).any { index ->
            val record = urls.opt(index)
            val url = when (record) {
                is String -> record
                is JSONArray -> record.optString(1)
                else -> ""
            }
            isSubscriptionURL(url)
        }
    }
}
