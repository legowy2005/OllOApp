package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollo.model.CardItem
import com.example.ollo.ui.screens.BleLogScreen
import com.example.ollo.ui.screens.CardEditScreen
import com.example.ollo.ui.screens.DeckListScreen
import com.example.ollo.ui.screens.SettingsScreen
import com.example.ollo.ui.screens.SyncScreen
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OlloDarkBg

sealed interface OlloScreen {
    data object DeckList : OlloScreen
    data class CardEdit(val card: CardItem) : OlloScreen
    data object Sync : OlloScreen
    data object BleLog : OlloScreen
    data object Settings : OlloScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OlloDarkBg
                ) {
                    OlloAppNav()
                }
            }
        }
    }
}

@Composable
fun OlloAppNav(viewModel: OlloViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf<OlloScreen>(OlloScreen.DeckList) }

    when (val screen = currentScreen) {
        is OlloScreen.DeckList -> {
            DeckListScreen(
                viewModel = viewModel,
                onNavigateToEdit = { card -> currentScreen = OlloScreen.CardEdit(card) },
                onNavigateToSync = { currentScreen = OlloScreen.Sync },
                onNavigateToLog = { currentScreen = OlloScreen.BleLog },
                onNavigateToSettings = { currentScreen = OlloScreen.Settings }
            )
        }
        is OlloScreen.CardEdit -> {
            CardEditScreen(
                card = screen.card,
                viewModel = viewModel,
                onNavigateBack = { currentScreen = OlloScreen.DeckList }
            )
        }
        is OlloScreen.Sync -> {
            SyncScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = OlloScreen.DeckList }
            )
        }
        is OlloScreen.BleLog -> {
            BleLogScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = OlloScreen.DeckList }
            )
        }
        is OlloScreen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = OlloScreen.DeckList }
            )
        }
    }
}
