package com.mansoor.djapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) AppState.checkLastProblem(this)
        handleIntent(intent)
        setContent { DjAppUi(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "djapp" && data.host == "callback") {
            intent.data = null
            AppState.handleRedirect(data)
        }
    }
}
