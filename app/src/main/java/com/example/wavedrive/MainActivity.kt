package com.example.wavedrive

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.wavedrive.ui.theme.WaveDriveTheme
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.fixedRateTimer

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var tiltMessage by mutableStateOf("Flat")
    private var isConnected = false

    // IP adresa robota
    private val robotIp = "192.168.4.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicijalizacija senzora
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            WaveDriveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = tiltMessage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // Pokrećemo inicijalnu proveru konekcije
        checkConnection()

        // Periodična provera konekcije
        fixedRateTimer("connectionChecker", true, 0L, 5000) {
            checkConnection()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Funkcija za proveru konekcije sa robotom
    private fun checkConnection() {
        Thread {
            try {
                val url = URL("http://$robotIp")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val responseCode = connection.responseCode
                val currentConnectionStatus = responseCode == 200

                runOnUiThread {
                    if (currentConnectionStatus != isConnected) {
                        isConnected = currentConnectionStatus
                        val message = if (isConnected) "Connection successful" else "Connection lost"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    if (isConnected) {
                        isConnected = false
                        Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    // Funkcija za slanje JSON komande robotu
    private fun sendSpeedCommand(leftSpeed: Int, rightSpeed: Int) {
        val jsonCommand = """{"T":1,"L":$leftSpeed,"R":$rightSpeed}"""
        val url = "http://$robotIp/js?json=$jsonCommand"

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Error", e.message ?: "Unknown error")
            }
        }.start()
    }

    // Logika za nagib telefona
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]

            tiltMessage = when {
                y < -3 && x in -3.0..3.0 -> {
                    sendSpeedCommand(255, 255)  // Napred
                    "Moving Forward"
                }
                y > 3 && x in -3.0..3.0 -> {
                    sendSpeedCommand(-255, -255)  // Nazad
                    "Moving Backward"
                }
                x < -3 && y in -3.0..3.0 -> {
                    sendSpeedCommand(255, -255)  // Rotacija Desno
                    "Rotating Right"
                }
                x > 3 && y in -3.0..3.0 -> {
                    sendSpeedCommand(-255, 255)  // Rotacija Levo
                    "Rotating Left"
                }
                y < -3 && x > 3 -> {
                    sendSpeedCommand(0, 255)  // Napred-Levo
                    "Forward-Left"
                }
                y < -3 && x < -3 -> {
                    sendSpeedCommand(255, 0)  // Napred-Desno
                    "Forward-Right"
                }
                y > 3 && x > 3 -> {
                    sendSpeedCommand(0, -255)  // Nazad-Levo
                    "Backward-Left"
                }
                y > 3 && x < -3 -> {
                    sendSpeedCommand(-255, 0)  // Nazad-Desno
                    "Backward-Right"
                }
                else -> {
                    sendSpeedCommand(0, 0)  // Zaustavljanje
                    "Flat - Stopped"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ova metoda trenutno nije potrebna
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WaveDriveTheme {
        Greeting("Flat")
    }
}