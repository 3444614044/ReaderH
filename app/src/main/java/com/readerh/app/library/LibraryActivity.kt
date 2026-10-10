package com.readerh.app.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readerh.app.R
import com.readerh.app.data.ArticleStore
import com.readerh.app.model.Article
import com.readerh.app.reader.ReaderActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibraryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private val items = mutableListOf<Article>()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        recycler = findViewById(R.id.recycler)
        empty = findViewById(R.id.empty)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(ArticleStore.get().listAll())
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun open(a: Article) {
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_ID, a.id)
            putExtra(ReaderActivity.EXTRA_URL, a.url)
            putExtra(ReaderActivity.EXTRA_TITLE, a.title)
            putExtra(ReaderActivity.EXTRA_HTML, a.contentHtml)
            putExtra(ReaderActivity.EXTRA_NEXT, "")
        })
    }

    private fun remove(a: Article) {
        ArticleStore.get().delete(a.id)
        reload()
    }

    private inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_article, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val a = items[position]
            h.tvTitle.text = a.title.ifBlank { a.url }
            val percent = if (a.totalPages > 0) (a.readPage + 1) * 100 / a.totalPages else 0
            h.tvMeta.text =
                "${a.site.ifBlank { "本地" }} · ${date(a.addedAt)} · 读到 ${a.readPage + 1}/${a.totalPages.coerceAtLeast(1)} 页（$percent%）"
            h.itemView.setOnClickListener { open(a) }
            h.btnDelete.setOnClickListener { remove(a) }
        }
    }

    private fun date(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))
}
