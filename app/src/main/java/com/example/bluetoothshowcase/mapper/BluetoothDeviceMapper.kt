package com.example.bluetoothshowcase.mapper

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.example.bluetoothshowcase.R
import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView
import javax.inject.Inject

class BluetoothDeviceMapper @Inject constructor(
    val context: Context
) {
    operator fun invoke(device: BluetoothDevice): BluetoothDeviceOnView {
        return BluetoothDeviceOnView(
            name = getName(device),
            address = device.address ?: "N/A",
        )
    }

    private fun getName(device: BluetoothDevice) : String =
        try {
            device.name ?: context.getString(R.string.ndash)
        } catch (e: SecurityException) {
            context.getString(R.string.ndash)
        }
}
