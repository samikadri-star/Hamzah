package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.SmcApp
import com.example.ui.SmcViewModel
import com.example.ui.SmcViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Setup factory and instantiate our central state companion
        val factory = SmcViewModelFactory(application)
        val viewModel = ViewModelProvider(this, factory)[SmcViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmcApp(viewModel = viewModel)
                }
            }
        }
    }
}
