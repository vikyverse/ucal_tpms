package com.ucal.tpms

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.containsKey
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lorenzofelletti.permissions.BuildConfig.DEBUG
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var btnStartScan: Button

    private lateinit var permissionManager: PermissionManager

    private lateinit var btManager: BluetoothManager
    private lateinit var bleScanManager: BleScanManager

    private lateinit var foundDevices: MutableList<BleDevice>

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
        bleScanManager = BleScanManager(btManager, 5000, scanCallback = BleScanCallback({
            val name = it?.device?.name
            val address = it?.device?.address
            var pressure = ""
            var temperature = ""
            val manufacturerData = it?.scanRecord?.manufacturerSpecificData
            var hexData = ""
            if (manufacturerData != null) {
                // Replace 0xFFFF with the actual manufacturer ID you want to filter by
                val manufacturerId = 0xFFFF
                if (manufacturerData.containsKey(manufacturerId)) {
                    val data = manufacturerData[manufacturerId]
                    for (b in data){
                        val st = String.format("%02X", b)
                        hexData += st
                    }
                    val sensorData: List<String> = hexToString(hexData).split('|')
                    pressure = sensorData[1]+" psi"
                    temperature = sensorData[3]+"°C"
                }
            }

            if (name.isNullOrBlank() || address.isNullOrBlank()) return@BleScanCallback

            val device = BleDevice(name, address, pressure, temperature)
            if (!foundDevices.contains(device)) {
                if (DEBUG) {
                    Log.d(
                        BleScanCallback::class.java.simpleName,
                        "${this.javaClass.enclosingMethod?.name} - Found device: $name"
                    )
                }
                foundDevices.add(device)
                adapter.notifyItemInserted(foundDevices.size - 1)
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
            if (DEBUG) Log.i(TAG, "${it.javaClass.simpleName}:${it.id} - onClick event")

            // Checks if the required permissions are granted and starts the scan if so, otherwise it requests them
            permissionManager checkRequestAndDispatch BLE_PERMISSION_REQUEST_CODE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.dispatchOnRequestPermissionsResult(requestCode, grantResults)
    }
    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val BLE_PERMISSION_REQUEST_CODE = 1
        @RequiresApi(Build.VERSION_CODES.S)
        private val blePermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

    private fun hexToString(hex: String): String {
        val stringBuilder = StringBuilder()
        for (i in hex.indices step 2) {
            val hexByte = hex.substring(i, i + 2)
            val intValue = hexByte.toInt(16)
            stringBuilder.append(intValue.toChar())
        }
        return stringBuilder.toString()
    }
}