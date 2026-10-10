package com.readerh.app

import android.app.Application
import com.readerh.app.data.ArticleStore
import com.readerh.app.util.ImageLoader

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ArticleStore.init(this)
        ImageLoader.init(this)
    }
}
