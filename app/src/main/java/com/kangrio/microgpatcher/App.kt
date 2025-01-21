package com.kangrio.microgpatcher

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        var _appContext: Context? = null

        fun getAppContext(): Context {
            return _appContext!!
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        _appContext = base
    }
}