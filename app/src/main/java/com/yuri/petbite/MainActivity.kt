package com.yuri.petbite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuri.petbite.ui.PetApp
import com.yuri.petbite.ui.PetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PetTheme { val model: PetViewModel = viewModel(); PetApp(model) } }
    }
}
