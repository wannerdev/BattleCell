package com.battlecell.app

import android.app.Application
import com.battlecell.app.core.AppContainer

class BattleCellApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext)
    }
}
