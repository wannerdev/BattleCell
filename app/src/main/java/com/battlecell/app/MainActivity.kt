package com.battlecell.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.battlecell.app.ui.BattleCellApp
import com.battlecell.app.ui.theme.BattleCellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val appContainer = (application as BattleCellApplication).appContainer
        setContent {
            BattleCellTheme {
                BattleCellApp(appContainer = appContainer)
            }
        }
    }
}
