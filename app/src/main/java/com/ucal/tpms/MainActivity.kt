package com.ucal.tpms

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.util.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lorenzofelletti.permissions.PermissionManager
import com.lorenzofelletti.permissions.dispatcher.dsl.checkPermissions
import com.lorenzofelletti.permissions.dispatcher.dsl.doOnDenied
import com.lorenzofelletti.permissions.dispatcher.dsl.doOnGranted
import com.lorenzofelletti.permissions.dispatcher.dsl.showRationaleDialog
import com.lorenzofelletti.permissions.dispatcher.dsl.withRequestCode
import com.ucal.tpms.blescanner.BleScanManager
import com.ucal.tpms.blescanner.adapter.BleDeviceAdapter
import com.ucal.tpms.blescanner.model.BleDevice
import com.ucal.tpms.blescanner.model.BleScanCallback
import com.ucal.tpms.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var btnStartScan: Button
    private lateinit var btnDownloadLog: Button

    private lateinit var permissionManager: PermissionManager

    private lateinit var btManager: BluetoothManager
    private lateinit var bleScanManager: BleScanManager

    private lateinit var foundDevices: MutableList<BleDevice>

    private val handler = android.os.Handler()
    private val scanDurationMillis: Long = 50000 // 90 seconds

    companion object {
        private const val BLE_PERMISSION_REQUEST_CODE = 1
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val blePermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_ADMIN
    )

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)
        permissionManager buildRequestResultsDispatcher {
            withRequestCode(BLE_PERMISSION_REQUEST_CODE) {
                checkPermissions(blePermissions)
                showRationaleDialog(getString(R.string.ble_permission_rationale))
                doOnGranted { bleScanManager.scanBleDevices() }
                doOnDenied {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_permissions_denied_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // RecyclerView handling
        val rvFoundDevices = findViewById<View>(R.id.rvDeviceList) as RecyclerView
        foundDevices = BleDevice.createBleDevicesList()
        val adapter = BleDeviceAdapter(foundDevices)
        rvFoundDevices.adapter = adapter
        rvFoundDevices.layoutManager = LinearLayoutManager(this)

        // BleManager creation
        btManager = getSystemService(BluetoothManager::class.java)

        // Define the target MAC address you want to scan for
        val targetMacAddress = "48:23:35:03:4F:3C" // Replace with your desired MAC address

        bleScanManager = BleScanManager(btManager, 5000, scanCallback = BleScanCallback({ it ->
            val name = it?.device?.name
            val address = it?.device?.address
            var pressure = ""
            var temperature = ""
            var accelerometer = ""
            var batteryPercentage = ""

            // Check if the discovered device's MAC address matches the target MAC address
            if (address != null && address == targetMacAddress) {
                val advertisementData: SparseArray<ByteArray>? = it?.scanRecord?.manufacturerSpecificData

                if (advertisementData != null) {
                    val manufacturerId = 0X7C50 // Replace with the actual manufacturer ID
                    val manufacturerData = advertisementData.get(manufacturerId)

                    if (manufacturerData != null) {
                        val advData = manufacturerData.joinToString(separator = "") {
                            String.format("%02X", it)
                        }
                        // If you want to convert the hexadecimal data to a human-readable string:
                        val advDataText = String(manufacturerData, Charset.forName("ASCII"))
                        val sensorValue = advDataText.split("|")

                        pressure = sensorValue[0]
                        temperature = sensorValue[2]
                        accelerometer = sensorValue[4]
                        batteryPercentage = sensorValue[5]
                    }
                }

                if (name.isNullOrBlank() || address.isNullOrBlank()) {
                    return@BleScanCallback
                }

                val device = BleDevice(name, address, pressure, temperature, accelerometer, batteryPercentage)
                if (!foundDevices.contains(device)) {
                    foundDevices.add(device)
                    adapter.notifyItemInserted(foundDevices.size - 1)
                }

                // Log the data to a text file
                logData(device)
            }
        }))

        // Adding the actions the manager must do before and after scanning
        bleScanManager.beforeScanActions.add { btnStartScan.isEnabled = false }
        bleScanManager.beforeScanActions.add {
            foundDevices.size.let {
                foundDevices.clear()
                adapter.notifyItemRangeRemoved(0, it)
            }
        }
        bleScanManager.afterScanActions.add { btnStartScan.isEnabled = true }

        // Adding the onclick listener to the start scan button
        btnStartScan = findViewById(R.id.btnStartScan)
        btnStartScan.setOnClickListener {
            // Checks if the required permissions are granted and starts the scan if so, otherwise, it requests them
            permissionManager checkRequestAndDispatch BLE_PERMISSION_REQUEST_CODE
        }

        //Download log
        btnDownloadLog = findViewById(R.id.btnDownloadLog)
        btnDownloadLog.setOnClickListener {
            downloadLog()
        }

        // Schedule the scan to run every 3 seconds
        scheduleAutoScan()
    }

    private fun scheduleAutoScan() {
        handler.postDelayed({
            bleScanManager.scanBleDevices()
            scheduleAutoScan() // Schedule the next scan
        }, scanDurationMillis)
    }

    private fun logData(device: BleDevice) {
        thread {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp: ${device.name}, ${device.address}, ${device.pressure}, ${device.temperature}, ${device.accelerometer}, ${device.batteryPercentage}\n"

            try {
                val fileName = "ble_scan_data.txt"
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

                FileWriter(file, true).use { writer ->
                    writer.append(logEntry)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun downloadLog() {
        val fileName = "ble_scan_data.txt"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        if (file.exists()) {
            try {
                val input = FileInputStream(file)
                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(outputDir, fileName)
                val output = FileOutputStream(outputFile)

                val buffer = ByteArray(1024)
                var length: Int

                while (input.read(buffer).also { length = it } > 0) {
                    output.write(buffer, 0, length)
                }

                output.flush()
                output.close()
                input.close()

                Toast.makeText(
                    this@MainActivity,
                    "Download completed. Check your Downloads folder.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("DownloadLog", "Error copying file: ${e.message}")
            }
        } else {
            Toast.makeText(
                this@MainActivity,
                "File not found. Perform a BLE scan to generate data first.",
                Toast.LENGTH_LONG
            ).show()
        }
    }



}
