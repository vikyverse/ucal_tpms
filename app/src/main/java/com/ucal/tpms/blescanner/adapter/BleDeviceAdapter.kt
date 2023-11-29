package com.ucal.tpms.blescanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ucal.tpms.R
import com.ucal.tpms.blescanner.model.BleDevice

class BleDeviceAdapter(private val devices: List<BleDevice>) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name)
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.device_address)
        val devicePressureTextView: TextView = itemView.findViewById(R.id.tyrePressure)
        val deviceTemperatureTextView: TextView = itemView.findViewById(R.id.tyreTemp)
        val deviceAccelerometerTextView: TextView = itemView.findViewById(R.id.accelerometer)
        val deviceBatteryPercentageTextView: TextView = itemView.findViewById(R.id.battery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val deviceView = inflater.inflate(R.layout.device_row_layout, parent, false)
        return ViewHolder(deviceView)
    }

    override fun onBindViewHolder(holder: BleDeviceAdapter.ViewHolder, position: Int) {
        val device = devices[position]
        val textViewName = holder.deviceNameTextView
        val textViewAddress = holder.deviceAddressTextView
        val textViewPressure = holder.devicePressureTextView
        val textViewTemperature = holder.deviceTemperatureTextView
        val textViewAccelerometer = holder.deviceAccelerometerTextView
        val textViewBattery = holder.deviceBatteryPercentageTextView
        textViewName.text = device.name
        textViewAddress.text = device.address
        textViewPressure.text = device.pressure
        textViewTemperature.text = device.temperature
        textViewAccelerometer.text = device.accelerometer
        textViewBattery.text = device.batteryPercentage
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}