package com.example.vedroid_test2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.vedroid_test2.ui.theme.Vedroidtest2Theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

data class MapPin(
    val id: String,
    val label: String,
//    val lat: Double,
//    val lng: Double,
//    val type: String,
//    val epoch: Long,
//    val isDeleted: Boolean = false,
)

data class MainActivityState(
    val pins: Map<String, MapPin> = emptyMap(),
)

class MainViewModel : ViewModel() {
    var state: MainActivityState by mutableStateOf(MainActivityState())
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            udpListener()
        }
    }

    //setters section
    suspend fun addNewPin(pin: MapPin) {
        // validate against DTO FIRST HERE!



    }
    //setters section

    //udp_logic
    private suspend fun udpListener() {
        val socket = DatagramSocket(9999)
        val buf = ByteArray(256)

        while (true) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val data = String(packet.data, 0, packet.length)

//                appendLblTxtReceiverUDP(data)
            } catch (e: IOException) {
                break  // сокет закрыт → выход
            }
        }
    }

    //end
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Vedroidtest2Theme {

                val mainViewModel: MainViewModel = viewModel() //спавним ебобаный стейтер
                val state by mainViewModel::state //спавним стейт и управляем через стейтер, ёпта

//                LaunchedEffect(Unit) {
//                    delay(3000)
//                    mainViewModel.appendLblTxtReceiverUDP("ass")
//                    mainViewModel.appendTxtGeneratedLocalDeltaAggregator(42)
//
//                    delay(3000)
//                    mainViewModel.updateLblTxtReceiverUDP("ASS")
//                    mainViewModel.updateLblTxtGeneratedLocalDeltaAggregator(99)
//                } // а это для дебуггинга

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { paddingValues ->

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(paddingValues),
                            ) {
                                Text("Приём: ")
                                Text("Передача: ")
                            }
                            Column(
                                modifier = Modifier
                                    .padding(paddingValues),
                            ) {
                                Text(text = state.lblTxtReceiverUDP)
                                Text(text = state.lblTxtGeneratedLocalDeltaAggregator)
                            }
                        }
                    }
                ) //можно по-разному, но уже лучше Scaffold будет один. и он СУПЕР полезен



            }
        }
    }
}

@Composable
fun MapPinCarousel(
    pins: List<MapPin>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = pins,
            key = { pin -> pin.id } // PERFORMANCE FOR COMPOSE
        ) { pin ->
            MapPinItem(
                pin = pin,
                modifier = Modifier
                    .width(120.dp)
//                    .animateItemPlacement() // Smooth animations
            )
        }
    }
}

@Composable
fun MapPinItem(
    pin: MapPin,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = pin.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pin.id.take(8),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}