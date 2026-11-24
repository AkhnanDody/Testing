package com.golda.veniceseedream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.golda.veniceseedream.ui.GalleryScreen
import com.golda.veniceseedream.ui.HomeScreen
import com.golda.veniceseedream.ui.MainViewModel
import com.golda.veniceseedream.ui.SettingsDrawerContent
import com.golda.veniceseedream.ui.theme.VeniceSeedreamTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeniceSeedreamTheme {
                VeniceSeedreamApp()
            }
        }
    }
}

private enum class Tab { HOME, GALLERY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeniceSeedreamApp(viewModel: MainViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.HOME) }
    val apiKey by viewModel.apiKey.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawerContent(
                savedApiKey = apiKey,
                onSave = { viewModel.saveApiKey(it) },
                onClear = { viewModel.clearApiKey() },
                onSaved = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Venice Seedream") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu / Pengaturan")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { tab = Tab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Beranda") },
                        label = { Text("Beranda") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.GALLERY,
                        onClick = {
                            tab = Tab.GALLERY
                            viewModel.refreshGallery()
                        },
                        icon = { Icon(Icons.Default.Image, contentDescription = "Galeri") },
                        label = { Text("Galeri") }
                    )
                }
            }
        ) { innerPadding ->
            when (tab) {
                Tab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    hasApiKey = apiKey.isNotBlank(),
                    onOpenGallery = { tab = Tab.GALLERY },
                    modifier = Modifier.padding(innerPadding)
                )
                Tab.GALLERY -> GalleryScreen(
                    viewModel = viewModel,
                    onUsePrompt = { prompt ->
                        viewModel.usePrompt(prompt)
                        tab = Tab.HOME
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
