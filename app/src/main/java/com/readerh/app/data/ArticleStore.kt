package com.readerh.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.readerh.app.model.Article

class ArticleStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "circle_reader.db", null, 1) {

    companion object {
        @Volatile
        private var instance: ArticleStore? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = ArticleStore(context.applicationContext)
                }
            }
        }

        fun get(): ArticleStore = instance ?: throw IllegalStateException("ArticleStore 未初始化")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS articles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE,
                title TEXT,
                site TEXT,
                content_html TEXT,
                text_content TEXT,
                added_at INTEGER,
                read_page INTEGER DEFAULT 0,
                total_pages INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS articles")
        onCreate(db)
    }

    fun upsert(article: Article): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", article.url)
            put("title", article.title)
            put("site", article.site)
            put("content_html", article.contentHtml)
            put("text_content", article.text)
            put("added_at", if (article.addedAt == 0L) System.currentTimeMillis() else article.addedAt)
            put("read_page", article.readPage)
            put("total_pages", article.totalPages)
        }
        val existing = findIdByUrl(article.url)
        return if (existing > 0) {
            db.update("articles", values, "id=?", arrayOf(existing.toString()))
            existing
        } else {
            db.insert("articles", null, values)
        }
    }

    fun findIdByUrl(url: String): Long {
        readableDatabase.rawQuery("SELECT id FROM articles WHERE url=?", arrayOf(url)).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else -1L
        }
    }

    fun findByUrl(url: String): Article? {
        readableDatabase.rawQuery("SELECT * FROM articles WHERE url=?", arrayOf(url)).use { c ->
            return if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    fun findById(id: Long): Article? {
        readableDatabase.rawQuery("SELECT * FROM articles WHERE id=?", arrayOf(id.toString()))
            .use { c ->
                return if (c.moveToFirst()) fromCursor(c) else null
            }
    }

    fun listAll(): List<Article> {
        val list = mutableListOf<Article>()
        readableDatabase.rawQuery("SELECT * FROM articles ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) list.add(fromCursor(c))
        }
        return list
    }

    fun updateProgress(id: Long, page: Int, total: Int) {
        val v = ContentValues().apply {
            put("read_page", page)
            put("total_pages", total)
        }
        writableDatabase.update("articles", v, "id=?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("articles", "id=?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("articles", null, null)
    }

    private fun fromCursor(c: android.database.Cursor): Article {
        fun col(name: String) = c.getColumnIndex(name)
        return Article(
            id = c.getLong(col("id")),
            url = c.getString(col("url")) ?: "",
            title = c.getString(col("title")) ?: "",
            site = c.getString(col("site")) ?: "",
            contentHtml = c.getString(col("content_html")) ?: "",
            text = c.getString(col("text_content")) ?: "",
            addedAt = c.getLong(col("added_at")),
            readPage = c.getInt(col("read_page")),
            totalPages = c.getInt(col("total_pages"))
        )
    }
}
