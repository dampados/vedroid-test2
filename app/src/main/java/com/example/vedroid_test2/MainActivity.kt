package com.example.vedroid_test2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
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

import com.daveanthonythomas.moshipack.MoshiPack
import java.net.InetAddress
import kotlin.math.ceil
import kotlin.random.Random

data class MapPin(
    val id: String,
    val label: String,
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double,
    val type: String,
    val epoch: Long,
    val isDeleted: Boolean = false,
)

data class MainActivityState(
    val pins: Map<String, MapPin> = emptyMap(),
)

class MainViewModel : ViewModel() {
    var state: MainActivityState by mutableStateOf(MainActivityState())
        private set
    private val moshiPack = MoshiPack()
//    private val pendingChunks = mutableMapOf<Int, MutableList<ByteArray?>>()  // Key: msgId, Value: List of chunks (null for placeholders)
    private val pendingChunks = mutableMapOf<Pair<Int, Short>, MutableList<ByteArray?>>()
//    private val mySenderId: Int = Random.nextInt() // UUID for rejecting same origin packets!
    private val mySenderId: Int = java.util.concurrent.ThreadLocalRandom.current().nextInt()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            launch { udpListener() }  // Start listener in its own child coroutine
            delay(5000)  // Brief delay to ensure listener is bound

            while (isActive) {  // Loop continues as long as the coroutine is active
                testEmission()
                delay(2000L)  // Delay for 1 second (1000 milliseconds)
            }

        }
    }

    //setters section
    //setters section

    private suspend fun testEmission() {
        val testPin = MapPin(
            id = Random.nextInt(0, 65536).toString(),
            label = "Тестовая метка",
            lat1 = 55.7558, lng1 = 37.6173,
            lat2 = 0.0, lng2 = 0.0,
            type = "test",
            epoch = 1,
            isDeleted = false
        )
        emitPin(testPin)
    }

    private suspend fun emitPin(pin: MapPin) {

        //#1 - get pin serialized
        //#2 - get it gzipped
        //#3 - generate msgId
        //#3 - physically emit, call for an abstract sender ( 1) smart UDP divider delivery func, 2) meshtastic usb black box driver) (LATER)

        //#1
        val serializedData = moshiPack.packToByteArray(pin)

        //#2 skip for now

        //#3
        val msgId = Random.nextInt(0, 65536).toShort()

        //#4
        udpPackageEmit(serializedData, msgId)

    }

    private fun udpPackageEmit(serializedData: ByteArray, msgId: Short) {
        val CHUNK_SIZE = 1400
        val socket = DatagramSocket()
        socket.broadcast = true

        val totalChunks = ceil(serializedData.size / CHUNK_SIZE.toDouble()).toInt()

        for (chunkIndex in 0 until totalChunks) {
            val start = chunkIndex * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, serializedData.size)
            val chunkData = serializedData.copyOfRange(start, end)

            // Create packet: [msgId(2)][total(1)][current(1)][data...]
            val packetData = ByteArray(8 + chunkData.size)

            packetData[0] = (mySenderId shr 24).toByte()
            packetData[1] = (mySenderId shr 16).toByte()
            packetData[2] = (mySenderId shr  8).toByte()
            packetData[3] =  mySenderId.toByte()

            packetData[4] = (msgId.toInt() shr 8).toByte() //0x12  // Random message ID high byte
            packetData[5] = msgId.toByte() //0x34  // Random message ID low byte
            packetData[6] = totalChunks.toByte()
            packetData[7] = chunkIndex.toByte()
            System.arraycopy(chunkData, 0, packetData, 8, chunkData.size)

            // Broadcast to everyone on network
            val packet = DatagramPacket(
                packetData, packetData.size,
                InetAddress.getByName("255.255.255.255"), 9999
            )
            socket.send(packet)
        }

        socket.close()
    }

    private suspend fun udpListener() {
        val socket = DatagramSocket(9999)
        val buffer = ByteArray(1500)

        while (isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                val data = packet.data.copyOf(packet.length)  // Extract received data

                // Parse header
                if (data.size < 8) continue  // Invalid packet

                // parse senderId
                val senderId = (data[0].toInt() and 0xFF shl 24) or
                                (data[1].toInt() and 0xFF shl 16) or
                                (data[2].toInt() and 0xFF shl  8) or
                                (data[3].toInt() and 0xFF)

                if (senderId == mySenderId) continue // THATS MY ORIGIN protection

                val msgId = (data[4].toInt() shl 8) or data[5].toInt()  // 2-byte msgId
                val totalChunks = data[6].toInt() and 0xFF  // Unsigned byte
                val currentChunk = data[7].toInt() and 0xFF  // Unsigned byte
                val chunkData = data.copyOfRange(8, data.size)


                // Initialize chunk list if new msgId
                val messageKey = senderId to msgId.toShort() //composite key!
                if (!pendingChunks.containsKey(messageKey)) {
                    pendingChunks[messageKey] = MutableList(totalChunks) { null }
                }

                // Store chunk
                val chunks = pendingChunks[messageKey]!!
                if (currentChunk < chunks.size) {
                    chunks[currentChunk] = chunkData
                }

                // Check if all chunks received
                if (chunks.all { it != null }) {
                    // Reassemble full data
                    val fullData = chunks.flatMap { it!!.toList() }.toByteArray()

                    // UNZIPPING  UNZIPPING  UNZIPPING  UNZIPPING
                    // val unzippedData = decompress(fullData)

                    // Deserialize with MoshiPack
                    val pin = moshiPack.unpack<MapPin>(fullData)  // Assuming MapPin is registered in Moshi

                    // Update state (replace entire map with this single pin for simplicity)
//                    state = state.copy(pins = mapOf(pin.id to pin))
                    state = state.copy(pins = state.pins + (pin.id to pin))

                    // Clean up (at the end)
                    pendingChunks.remove(messageKey)
                }
            } catch (e: IOException) {
                // Handle socket errors gracefully, e.g., log and continue
            }
        }
        socket.close()
    }



}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Vedroidtest2Theme {

                val mainViewModel: MainViewModel = viewModel() //спавним ебобаный стейтер
                val state by mainViewModel::state //спавним стейт и управляем через стейтер, ёпта

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
//                                Text(text = state.lblTxtReceiverUDP)
//                                Text(text = state.lblTxtGeneratedLocalDeltaAggregator)
                            }
                        }

                        val pins = state.pins
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Received pins (${pins.size}):",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (pins.isEmpty()) {
                                Text("No pins received yet.")
                            } else {
                                pins.values.forEach { pin ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "ID: ${pin.id}",
                                                style = MaterialTheme.typography.bodyMedium,
//                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("Label: ${pin.label}")
                                            Text("Lat/Lng: ${pin.lat1}, ${pin.lng1}")
                                            Text("Epoch: ${pin.epoch} | Deleted: ${pin.isDeleted}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) //можно по-разному, но уже лучше Scaffold будет один. и он СУПЕР полезен



            }
        }
    }
}

//@Composable
//fun MapPinCarousel(
//    pins: List<MapPin>,
//    modifier: Modifier = Modifier
//) {
//    LazyRow(
//        modifier = modifier,
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
//        contentPadding = PaddingValues(horizontal = 16.dp)
//    ) {
//        items(
//            items = pins,
//            key = { pin -> pin.id } // PERFORMANCE FOR COMPOSE
//        ) { pin ->
//            MapPinItem(
//                pin = pin,
//                modifier = Modifier
//                    .width(120.dp)
////                    .animateItemPlacement() // Smooth animations
//            )
//        }
//    }
//}
//
//@Composable
//fun MapPinItem(
//    pin: MapPin,
//    modifier: Modifier = Modifier
//) {
//    Card(
//        modifier = modifier,
//        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//    ) {
//        Column(
//            modifier = Modifier.padding(12.dp)
//        ) {
//            Text(
//                text = pin.name,
//                style = MaterialTheme.typography.bodyMedium,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis
//            )
//            Text(
//                text = pin.id.take(8),
//                style = MaterialTheme.typography.labelSmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//    }
//}