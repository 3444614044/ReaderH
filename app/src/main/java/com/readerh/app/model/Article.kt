package com.readerh.app.model

data class Article(
    val id: Long = 0,
    val url: String = "",
    val title: String = "",
    val site: String = "",
    val contentHtml: String = "",
    val text: String = "",
    val addedAt: Long = 0,
    var readPage: Int = 0,
    var totalPages: Int = 0
)
