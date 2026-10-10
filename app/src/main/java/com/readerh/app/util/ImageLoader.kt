package com.readerh.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 极简图片加载器：内存 LruCache + 文件缓存 + HttpURLConnection 下载。
 */
object ImageLoader {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    private const val TIMEOUT = 8000

    private val memory: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(64 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private var cacheDir: File? = null

    fun init(context: android.content.Context) {
        cacheDir = File(context.cacheDir, "img").apply { mkdirs() }
    }

    private fun key(url: String): String = try {
        val md = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        md.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        url.hashCode().toString()
    }

    private fun fileFor(url: String): File? = cacheDir?.let { File(it, key(url)) }

    /** 阻塞式获取，供后台线程调用 */
    fun get(url: String, maxWidth: Int): Bitmap? {
        memory.get(url)?.let { return it }
        val f = fileFor(url)
        if (f != null && f.exists()) {
            decode(f, maxWidth)?.let { memory.put(url, it); return it }
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", url)
            conn.inputStream.use { input ->
                if (f != null) {
                    FileOutputStream(f).use { out -> input.copyTo(out) }
                    decode(f, maxWidth)
                } else {
                    decodeBytes(input.readBytes(), maxWidth)
                }
            }?.also { memory.put(url, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(file: File, maxWidth: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight, maxWidth)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        return try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBytes(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight, maxWidth)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun calcSample(w: Int, h: Int, maxWidth: Int): Int {
        if (w <= 0 || maxWidth <= 0) return 1
        var s = 1
        while (w / s > maxWidth * 2) s *= 2
        return s
    }

    fun clearMemory() = memory.evictAll()

    fun clearDisk() {
        cacheDir?.listFiles()?.forEach { it.delete() }
        memory.evictAll()
    }
}
