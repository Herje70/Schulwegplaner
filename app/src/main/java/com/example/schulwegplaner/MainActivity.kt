@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.schulwegplaner

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import androidx.core.content.edit


@Serializable
data class HafasDeparture(
    @kotlinx.serialization.SerialName("when") val actualWhen: String? = null,
    val plannedWhen: String? = null,
    val delay: Int? = null,
    val direction: String? = null,
    val line: Line? = null,
    val stop: Stop? = null
)

@Serializable
data class Stop(val name: String? = null)

@Serializable
data class Line(val name: String? = null, val mode: String? = null)

// Einheitliches Datenmodell für die Anzeige im UI (unabhängig von Online/Offline)
data class CleanUiConnection(
    val type: String,               // "S-Bahn" oder "Bus"
    val lineName: String,           // z.B. "S2" oder "Bus 192"
    val fromStop: String,           // Start-Haltestelle
    val departureTime: LocalTime,
    val delayMinutes: Int,          // Echtzeitverspätung
    val toStop: String,             // Ziel-Haltestelle
    val arrivalTime: LocalTime,
    val requiredWalkBuffer: Int,     // Gehzeit-Puffer
    val isLive: Boolean             // Wahr, wenn aus der Live-API geladen
)


@Serializable
data class OfflineConnection(
    val type: String,
    val lineName: String,
    val fromStop: String,
    val departureTime: String,
    val toStop: String,
    val arrivalTime: String,
    val requiredWalkBuffer: Int
) {
    fun toCleanUiConnection(): CleanUiConnection {
        return CleanUiConnection(
            type = type,
            lineName = lineName,
            fromStop = fromStop,
            departureTime = LocalTime.parse(departureTime.trim()),
            delayMinutes = 0,
            toStop = toStop,
            arrivalTime = LocalTime.parse(arrivalTime.trim()),
            requiredWalkBuffer = requiredWalkBuffer,
            isLive = false
        )
    }
}

object LocalTimetable {
    private val defaultConnections = listOf(
        // Vormittags-Verbindungen (neu hinzugefügt)
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(8, 44), 0, "Bahnhof Zschortau", LocalTime.of(8, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(9, 44), 0, "Bahnhof Zschortau", LocalTime.of(9, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(10, 44), 0, "Bahnhof Zschortau", LocalTime.of(10, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(11, 44), 0, "Bahnhof Zschortau", LocalTime.of(11, 49), 15, false),

        // Vormittags & Nachmittags S-Bahn (Alle 30 Min)
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(9, 14), 0, "Bahnhof Zschortau", LocalTime.of(9, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(9, 44), 0, "Bahnhof Zschortau", LocalTime.of(9, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(10, 14), 0, "Bahnhof Zschortau", LocalTime.of(10, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(10, 44), 0, "Bahnhof Zschortau", LocalTime.of(10, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(11, 14), 0, "Bahnhof Zschortau", LocalTime.of(11, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(11, 44), 0, "Bahnhof Zschortau", LocalTime.of(11, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(12, 14), 0, "Bahnhof Zschortau", LocalTime.of(12, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(12, 44), 0, "Bahnhof Zschortau", LocalTime.of(12, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(13, 14), 0, "Bahnhof Zschortau", LocalTime.of(13, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(13, 44), 0, "Bahnhof Zschortau", LocalTime.of(13, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(14, 14), 0, "Bahnhof Zschortau", LocalTime.of(14, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(14, 44), 0, "Bahnhof Zschortau", LocalTime.of(14, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(15, 14), 0, "Bahnhof Zschortau", LocalTime.of(15, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(15, 44), 0, "Bahnhof Zschortau", LocalTime.of(15, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(16, 14), 0, "Bahnhof Zschortau", LocalTime.of(16, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(16, 44), 0, "Bahnhof Zschortau", LocalTime.of(16, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(17, 14), 0, "Bahnhof Zschortau", LocalTime.of(17, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(17, 44), 0, "Bahnhof Zschortau", LocalTime.of(17, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(18, 14), 0, "Bahnhof Zschortau", LocalTime.of(18, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(18, 44), 0, "Bahnhof Zschortau", LocalTime.of(18, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(19, 14), 0, "Bahnhof Zschortau", LocalTime.of(19, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(19, 44), 0, "Bahnhof Zschortau", LocalTime.of(19, 49), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(20, 14), 0, "Bahnhof Zschortau", LocalTime.of(20, 19), 15, false),
        CleanUiConnection("S-Bahn", "S2", "Delitzsch unt Bf", LocalTime.of(20, 44), 0, "Bahnhof Zschortau", LocalTime.of(20, 49), 15, false),

        // Busverbindungen (Vormittag & Schul-Stoßzeiten)
        CleanUiConnection("Bus", "Linie 192", "Delitzsch (Schule)", LocalTime.of(9, 30), 0, "Schule Zschortau", LocalTime.of(9, 45), 5, false),
        CleanUiConnection("Bus", "Linie 207", "Delitzsch unt Bf", LocalTime.of(10, 20), 0, "Zschortau Neue Str", LocalTime.of(10, 35), 15, false),
        CleanUiConnection("Bus", "Linie 192", "Delitzsch (Schule)", LocalTime.of(11, 30), 0, "Schule Zschortau", LocalTime.of(11, 45), 5, false),

        CleanUiConnection("Bus", "Linie 192", "Delitzsch (Schule)", LocalTime.of(12, 50), 0, "Schule Zschortau", LocalTime.of(13, 5), 5, false),
        CleanUiConnection("Bus", "Linie 192", "Delitzsch (Schule)", LocalTime.of(13, 30), 0, "Schule Zschortau", LocalTime.of(13, 45), 5, false),
        CleanUiConnection("Bus", "Linie 192", "Delitzsch (Schule)", LocalTime.of(14, 35), 0, "Schule Zschortau", LocalTime.of(14, 50), 5, false),
        CleanUiConnection("Bus", "Linie 195", "Delitzsch (Schule)", LocalTime.of(15, 40), 0, "Schule Zschortau", LocalTime.of(15, 55), 5, false),

        CleanUiConnection("Bus", "Linie 211", "Delitzsch unt Bf", LocalTime.of(13, 44), 0, "Zschortau Neue Str", LocalTime.of(14, 5), 15, false),
        CleanUiConnection("Bus", "Linie 211", "Delitzsch unt Bf", LocalTime.of(14, 44), 0, "Zschortau Neue Str", LocalTime.of(15, 5), 15, false),
        CleanUiConnection("Bus", "Linie 211", "Delitzsch unt Bf", LocalTime.of(15, 44), 0, "Zschortau Neue Str", LocalTime.of(16, 5), 15, false)
    )

    var connections: List<CleanUiConnection> = defaultConnections
        private set

    fun loadInitial(context: Context) {
        val prefs = context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("timetable_json", null)
        if (cachedJson != null) {
            try {
                val parsed = Json.decodeFromString<List<OfflineConnection>>(cachedJson)
                connections = parsed.map { it.toCleanUiConnection() }
            } catch (_: Exception) {
                // Bei Fehlern bleiben die defaultConnections erhalten
                connections = defaultConnections
            }
        } else {
            connections = defaultConnections
        }
    }

    fun getLastSyncTime(context: Context): String {
        val prefs = context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
        return prefs.getString("timetable_last_sync", "Standard") ?: "Standard"
    }

    fun saveTimetable(context: Context, newConnections: List<OfflineConnection>): Boolean {
        return try {
            connections = newConnections.map { it.toCleanUiConnection() }
            val jsonString = Json.encodeToString(newConnections)
            val prefs = context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
            val formattedNow = java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
            prefs.edit {
                putString("timetable_json", jsonString)
                putString("timetable_last_sync", formattedNow)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

interface TransportApiService {
    @GET("departureStationBoard")
    suspend fun getDepartures(
        @Query("station") station: String = "8010077",
        @Query("results") results: Int = 20
    ): List<HafasDeparture>

    @GET
    suspend fun downloadTimetable(@Url url: String): List<OfflineConnection>
}

object NetworkClient {
    private val json = Json { ignoreUnknownKeys = true }

    // Erhöht auf 15 Sekunden, da die öffentliche API oft langsam reagiert
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: TransportApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://bahn.expert/api/hafas/v3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TransportApiService::class.java)
    }
}


class MainViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    var lastSyncTime by mutableStateOf("Standard")
        private set

    sealed interface UiState {
        object Idle : UiState
        object Loading : UiState
        data class Success(val connections: List<CleanUiConnection>, val isLive: Boolean) : UiState
        data class ApiFailed(val message: String, val lastTimeInput: String) : UiState // NEUER STATUS
    }

    init {
        LocalTimetable.loadInitial(application)
        lastSyncTime = LocalTimetable.getLastSyncTime(application)
        fetchRemoteTimetable()
    }

    private fun fetchRemoteTimetable() {
        viewModelScope.launch {
            try {
                val url = "https://raw.githubusercontent.com/Herje70/Schulwegplaner/main/timetable.json"
                val response = NetworkClient.apiService.downloadTimetable(url)
                if (LocalTimetable.saveTimetable(getApplication(), response)) {
                    lastSyncTime = LocalTimetable.getLastSyncTime(getApplication())
                }
            } catch (_: Exception) {
                // Fehler ignorieren
            }
        }
    }

    // Lädt NUR Live-Daten
    fun fetchLiveConnections(schoolEndTimeText: String) {
        uiState = UiState.Loading

        val parsedEndTime = try {
            LocalTime.parse(schoolEndTimeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: DateTimeParseException) {
            uiState = UiState.ApiFailed("Ungültiges Uhrzeitformat.", schoolEndTimeText)
            return
        }

        viewModelScope.launch {
            try {
                val rawDepartures = NetworkClient.apiService.getDepartures()

                val liveResults = rawDepartures.mapNotNull { dep ->
                    // Filter für Richtung Zschortau/Leipzig
                    val dir = dep.direction ?: ""
                    val isTowardsZschortau = dir.contains("Zschortau", ignoreCase = true) || 
                                             dir.contains("Leipzig", ignoreCase = true) ||
                                             dir.contains("Borna", ignoreCase = true) ||
                                             dir.contains("Geithain", ignoreCase = true)
                    
                    if (!isTowardsZschortau) return@mapNotNull null
                    
                    val timeString = dep.actualWhen ?: dep.plannedWhen ?: return@mapNotNull null
                    val depTimeParsed = try {
                        ZonedDateTime.parse(timeString).toLocalTime()
                    } catch (_: Exception) { return@mapNotNull null }
                    
                    val delayMin = (dep.delay ?: 0) / 60
                    val isTrain = dep.line?.mode == "train" || dep.line?.name?.startsWith("S") == true
                    val buffer = if (isTrain) 15 else 5
                    
                    // Abfahrtstafeln enthalten keine Ankunftszeit am Ziel, daher schätzen wir
                    val estimatedArrival = depTimeParsed.plusMinutes(if (isTrain) 5 else 15)

                    val earliestPossibleDeparture = parsedEndTime.plusMinutes(buffer.toLong())

                    if (!depTimeParsed.isBefore(earliestPossibleDeparture)) {
                        CleanUiConnection(
                            type = if (isTrain) "S-Bahn" else "Bus",
                            lineName = dep.line?.name ?: "ÖPNV",
                            fromStop = dep.stop?.name ?: "Delitzsch",
                            departureTime = depTimeParsed,
                            delayMinutes = delayMin,
                            toStop = "Zschortau (geschätzt)",
                            arrivalTime = estimatedArrival,
                            requiredWalkBuffer = buffer,
                            isLive = true
                        )
                    } else null
                }.sortedBy { it.departureTime }

                uiState = UiState.Success(liveResults, isLive = true)

            } catch (_: Exception) {
                // Automatischer Fallback auf den Offline-Fahrplan, da die Live-API offline oder überlastet ist
                val offlineResults = LocalTimetable.connections.filter { connection ->
                    val earliestPossibleDeparture = parsedEndTime.plusMinutes(connection.requiredWalkBuffer.toLong())
                    !connection.departureTime.isBefore(earliestPossibleDeparture)
                }.sortedBy { it.departureTime }
                uiState = UiState.Success(offlineResults, isLive = false)
            }
        }
    }

    // Wird nur aufgerufen, wenn der Nutzer EXPLIZIT den Offline-Plan sehen will
    fun loadOfflineFallback(schoolEndTimeText: String) {
        val parsedEndTime = LocalTime.parse(schoolEndTimeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        val offlineResults = LocalTimetable.connections.filter { connection ->
            val earliestPossibleDeparture = parsedEndTime.plusMinutes(connection.requiredWalkBuffer.toLong())
            !connection.departureTime.isBefore(earliestPossibleDeparture)
        }.sortedBy { it.departureTime }

        uiState = UiState.Success(offlineResults, isLive = false)
    }

    fun findBestConnections(schoolEndTimeText: String) {
        fetchLiveConnections(schoolEndTimeText)
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var timeInput by remember { mutableStateOf("13:15") }
    var showTimePicker by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Schulweg-Planer",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            "Ehrenberg-Gymnasium ➔ Zschortau",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Info
                Surface(
                    color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Datenstand: ${viewModel.lastSyncTime}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSecondaryContainer
                    )
                }

                // Time Input Section
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Wann endet der Unterricht?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { timeInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                            leadingIcon = { Icon(Icons.Rounded.AccessTime, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showTimePicker = true },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.EditCalendar, contentDescription = "Zeit wählen")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.findBestConnections(timeInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verbindungen finden", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showTimePicker) {
                    val parts = timeInput.split(":")
                    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 13
                    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 15

                    val timePickerState = rememberTimePickerState(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        is24Hour = true
                    )

                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                        confirmButton = {
                            Button(
                                onClick = {
                                    val formattedHour = timePickerState.hour.toString().padStart(2, '0')
                                    val formattedMinute = timePickerState.minute.toString().padStart(2, '0')
                                    timeInput = "$formattedHour:$formattedMinute"
                                    showTimePicker = false
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
                        },
                        text = {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        "Uhrzeit wählen",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(bottom = 20.dp)
                                    )
                                    
                                    TimePicker(state = timePickerState)
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    FilledTonalButton(
                                        onClick = {
                                            val now = LocalTime.now()
                                            timeInput = now.format(DateTimeFormatter.ofPattern("HH:mm"))
                                            showTimePicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Aktuelle Zeit übernehmen")
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Results Area
                when (val state = viewModel.uiState) {
                    is MainViewModel.UiState.Idle -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Rounded.DirectionsTransit,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Bereit für die Suche",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.outline
                            )
                        }
                    }
                    is MainViewModel.UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                        }
                    }
                    is MainViewModel.UiState.ApiFailed -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(state.message, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("Verbindung zum Server fehlgeschlagen.", fontSize = 12.sp, textAlign = TextAlign.Center)
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { viewModel.fetchLiveConnections(state.lastTimeInput) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                                ) {
                                    Text("Erneut versuchen")
                                }
                                TextButton(onClick = { viewModel.loadOfflineFallback(state.lastTimeInput) }) {
                                    Text("Offline-Plan nutzen", color = colorScheme.error)
                                }
                            }
                        }
                    }
                    is MainViewModel.UiState.Success -> {
                    StatusIndicator(isBackupActive = !state.isLive)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.connections.isEmpty()) {
                            Text("Keine Fahrten nach dieser Zeit gefunden.", color = colorScheme.outline)
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.connections.take(5)) { connection ->
                                    ConnectionDisplayCard(connection)
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun StatusIndicator(isBackupActive: Boolean) {
    val containerColor = if (isBackupActive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) 
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val contentColor = if (isBackupActive) MaterialTheme.colorScheme.error 
                      else MaterialTheme.colorScheme.primary
    val icon = if (isBackupActive) Icons.Rounded.WifiOff else Icons.Rounded.CloudDone
    val text = if (isBackupActive) "Offline-Plan aktiv (Keine Live-Daten)" else "Echtzeit-Daten aktiv"

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConnectionDisplayCard(connection: CleanUiConnection) {
    val isTrain = connection.type.contains("S-Bahn", ignoreCase = true)
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isTrain) Color(0xFF0056D2) else Color(0xFFFF9800),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isTrain) Icons.Rounded.Train else Icons.Rounded.DirectionsBus,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = connection.lineName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = connection.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.outline
                        )
                    }
                }

                if (connection.isLive) {
                    val delay = connection.delayMinutes
                    Surface(
                        color = if (delay > 0) colorScheme.errorContainer else Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (delay > 0) "+$delay Min" else "Pünktlich",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (delay > 0) colorScheme.error else Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Abfahrt", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                    Text(
                        text = connection.departureTime.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(connection.fromStop, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Ankunft", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                    Text(
                        text = connection.arrivalTime.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(connection.toStop, style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.End)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Fußweg-Puffer: ${connection.requiredWalkBuffer} Min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.outline
                )
            }
        }
    }
}
