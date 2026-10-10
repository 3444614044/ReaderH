package com.readerh.app.util

import java.net.URLEncoder

data class SearchEngine(
    /** 显示名 */
    val name: String,
    /** 地址栏里显示的短标记 */
    val badge: String,
    /** 搜索地址模板，%s 会被替换成编码后的关键词 */
    val template: String
) {
    fun urlFor(keyword: String): String =
        template.replace("%s", URLEncoder.encode(keyword, "UTF-8"))
}

/**
 * 内置搜索引擎，可在地址栏左侧按钮或设置页随时切换。
 */
object SearchEngines {
    val all = listOf(
        SearchEngine("百度", "百", "https://www.baidu.com/s?wd=%s"),
        SearchEngine("必应", "B", "https://www.bing.com/search?q=%s"),
        SearchEngine("搜狗", "搜", "https://www.sogou.com/web?query=%s"),
        SearchEngine("360 搜索", "360", "https://www.so.com/s?q=%s"),
        SearchEngine("Google", "G", "https://www.google.com/search?q=%s"),
        SearchEngine("DuckDuckGo", "D", "https://duckduckgo.com/?q=%s"),
        SearchEngine("夸克", "夸", "https://quark.sm.cn/s?q=%s"),
        SearchEngine("头条搜索", "头", "https://so.toutiao.com/search?keyword=%s")
    )

    fun of(index: Int): SearchEngine = all[index.coerceIn(0, all.lastIndex)]

    fun names(): Array<String> = all.map { it.name }.toTypedArray()
}
