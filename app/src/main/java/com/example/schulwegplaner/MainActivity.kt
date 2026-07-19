package com.example.schulwegplaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import retrofit2.http.Url
import kotlinx.serialization.encodeToString
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit


@Serializable
data class JourneyResponse(val journeys: List<Journey>? = null)

@Serializable
data class Journey(val legs: List<Leg>? = null)

@Serializable
data class Leg(
    val origin: Stop? = null,
    val destination: Stop? = null,
    val departure: String? = null,
    val arrival: String? = null,
    val departureDelay: Int? = null,
    val line: Line? = null,
    val walking: Boolean? = false
)

@Serializable
data class Stop(val name: String? = null)

@Serializable
data class Line(val name: String? = null, val mode: String? = null)

// Einheitliches Datenmodell für die Anzeige im UI (unabhängig von Online/Offline)
data class CleanUiConnection(
    val type: String,               // "S-Bahn" oder "Bus"
    val lineName: String,           // z.B. "S2" oder "Bus 191"
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

        CleanUiConnection("Bus", "Linie 191", "Delitzsch (Schule)", LocalTime.of(12, 50), 0, "Schule Zschortau", LocalTime.of(13, 5), 5, false),
        CleanUiConnection("Bus", "Linie 191", "Delitzsch (Schule)", LocalTime.of(13, 30), 0, "Schule Zschortau", LocalTime.of(13, 45), 5, false),
        CleanUiConnection("Bus", "Linie 191", "Delitzsch (Schule)", LocalTime.of(14, 35), 0, "Schule Zschortau", LocalTime.of(14, 50), 5, false),
        CleanUiConnection("Bus", "Linie 195", "Delitzsch (Schule)", LocalTime.of(15, 40), 0, "Schule Zschortau", LocalTime.of(15, 55), 5, false)
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
            } catch (e: Exception) {
                // Bei Fehlern bleiben die defaultConnections erhalten
            }
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
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
            prefs.edit()
                .putString("timetable_json", jsonString)
                .putString("timetable_last_sync", formattedNow)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}

interface TransportApiService {
    @GET("journeys")
    suspend fun getConnections(
        @Query("from") from: String = "Delitzsch",
        @Query("to") to: String = "Zschortau",
        @Query("results") results: Int = 8
    ): JourneyResponse

    @GET
    suspend fun downloadTimetable(@Url url: String): List<OfflineConnection>
}

object NetworkClient {
    private val json = Json { ignoreUnknownKeys = true }

    // Erhöht auf 8 Sekunden für schlechten Empfang auf dem Schulhof
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val apiService: TransportApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://v6.db.transport.rest/")
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
            } catch (e: Exception) {
                // Fehler beim Hintergrund-Laden ignorieren
            }
        }
    }

    // Lädt NUR Live-Daten
    fun fetchLiveConnections(schoolEndTimeText: String) {
        uiState = UiState.Loading

        val parsedEndTime = try {
            LocalTime.parse(schoolEndTimeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: DateTimeParseException) {
            uiState = UiState.ApiFailed("Ungültiges Uhrzeitformat.", schoolEndTimeText)
            return
        }

        viewModelScope.launch {
            try {
                val response = NetworkClient.apiService.getConnections()
                val rawJourneys = response.journeys ?: emptyList()

                val liveResults = rawJourneys.mapNotNull { journey ->
                    val transitLeg = journey.legs?.firstOrNull { it.walking == false } ?: return@mapNotNull null
                    val depTimeParsed = ZonedDateTime.parse(transitLeg.departure).toLocalTime()
                    val arrTimeParsed = ZonedDateTime.parse(transitLeg.arrival).toLocalTime()
                    val delayMin = (transitLeg.departureDelay ?: 0) / 60
                    val isTrain = transitLeg.line?.mode == "train"
                    val buffer = if (isTrain) 15 else 5
                    val earliestPossibleDeparture = parsedEndTime.plusMinutes(buffer.toLong())

                    if (!depTimeParsed.isBefore(earliestPossibleDeparture)) {
                        CleanUiConnection(
                            type = if (isTrain) "S-Bahn" else "Bus",
                            lineName = transitLeg.line?.name ?: "ÖPNV",
                            fromStop = transitLeg.origin?.name ?: "Abfahrt",
                            departureTime = depTimeParsed,
                            delayMinutes = delayMin,
                            toStop = transitLeg.destination?.name ?: "Ankunft",
                            arrivalTime = arrTimeParsed,
                            requiredWalkBuffer = buffer,
                            isLive = true
                        )
                    } else null
                }.sortedBy { it.departureTime }

                uiState = UiState.Success(liveResults, isLive = true)

            } catch (e: Exception) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Text(text = "Schulweg-Planer", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Ehrenberg-Gymnasium Delitzsch ➔ Zschortau",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Zeit-Eingabefeld
        OutlinedTextField(
            value = timeInput,
            onValueChange = { timeInput = it },
            label = { Text("Unterrichtsende (z.B. 13:15)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { showTimePicker = true }) {
                    Text("⏰", fontSize = 20.sp)
                }
            }
        )

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
                confirmButton = {
                    TextButton(
                        onClick = {
                            val formattedHour = timePickerState.hour.toString().padStart(2, '0')
                            val formattedMinute = timePickerState.minute.toString().padStart(2, '0')
                            timeInput = "$formattedHour:$formattedMinute"
                            showTimePicker = false
                        }
                    ) {
                        Text("Auswählen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Abbrechen")
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TimePicker(state = timePickerState)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Such-Button
        Button(
            onClick = { viewModel.findBestConnections(timeInput) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verbindung suchen")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State Verteilung
        // UI ZUSTANDS-VERZWEIGUNG
        when (val state = viewModel.uiState) {
            is MainViewModel.UiState.Idle -> {
                Text("Tippe auf das Feld, um die Uhrzeit einzustellen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is MainViewModel.UiState.Loading -> {
                CircularProgressIndicator()
                Text("Frage DB-Server ab...", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
            }
            is MainViewModel.UiState.ApiFailed -> {
                // NEU: Fehler-Bildschirm mit zwei Optionen
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "⚠️", fontSize = 30.sp)
                        Text(
                            text = state.message,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "Schlechtes Internet oder API gestört.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { viewModel.fetchLiveConnections(state.lastTimeInput) }) {
                            Text("Nochmal versuchen")
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadOfflineFallback(state.lastTimeInput) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Plan-Fahrplan ansehen (Ohne Ausfall-Info)")
                        }
                    }
                }
            }
            is MainViewModel.UiState.Success -> {
                StatusIndicator(isBackupActive = !state.isLive, lastSyncTime = viewModel.lastSyncTime)
                Spacer(modifier = Modifier.height(12.dp))

                if (state.connections.isEmpty()) {
                    Text("Keine passenden Verbindungen gefunden.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.connections.take(4)) { connection ->
                            ConnectionDisplayCard(connection)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun StatusIndicator(isBackupActive: Boolean, lastSyncTime: String = "") {
    val backgroundColor = if (isBackupActive) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val contentColor = if (isBackupActive) Color(0xFFC62828) else Color(0xFF2E7D32)
    val icon = if (isBackupActive) Icons.Default.Warning else Icons.Default.Info
    val text = if (isBackupActive) {
        if (lastSyncTime.isNotEmpty() && lastSyncTime != "Standard") {
            "Offline-Fahrplan (Stand: $lastSyncTime) — DB-Server meldet Timeout"
        } else {
            "Offline-Fahrplan (Standard) — DB-Server meldet Timeout"
        }
    } else {
        "Live-Fahrplan (Echtzeit aktiv)"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ConnectionDisplayCard(connection: CleanUiConnection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${connection.type} — ${connection.lineName}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (connection.isLive) {
                    if (connection.delayMinutes > 0) {
                        Text(text = "+${connection.delayMinutes} Min.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    } else {
                        Text(text = "Pünktlich", color = Color(0xFF2E7D32), fontSize = 12.sp)
                    }
                } else {
                    Text(text = "Geplant", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Abfahrt: ${connection.departureTime} ab ${connection.fromStop}", fontSize = 14.sp)
            Text(text = "Ankunft: ${connection.arrivalTime} an ${connection.toStop}", fontSize = 14.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Benötigter Puffer zum Startpunkt: ${connection.requiredWalkBuffer} Minuten",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}