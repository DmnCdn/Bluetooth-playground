package com.example.bluetoothshowcase.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.example.bluetoothshowcase.service.BluetoothConnectionService

private const val TAG = "MyBroadcastReceiver"

interface BroadcastActionCallback {
    fun locationModeChanged()
    fun bluetoothDeviceFound(device: BluetoothDevice?)
    fun bluetoothBondStateChanged(device: BluetoothDevice?)
    fun startedBluetoothDiscovery()
    fun finishedBluetoothDiscovery()
    fun bluetoothScanModeChanged(state: Int)
    fun receivedBluetoothMessage(message: String?)
    fun bluetoothSocketStateChanged(state: Int, address: String?)
    fun bluetoothStateChanged(state: Int)
}

class MyBroadcastReceiver(
    private val actionCallback: BroadcastActionCallback
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        with(actionCallback) {
            when (action) {
                // location toggled
                LocationManager.MODE_CHANGED_ACTION -> locationModeChanged()
                // device found
                BluetoothDevice.ACTION_FOUND ->
                    bluetoothDeviceFound(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                // bond state changed
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    try {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        bluetoothBondStateChanged(device)
                    } catch (e: SecurityException) {
                        bluetoothBondStateChanged(null)
                    }
                }
                // finished discovering devices
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> finishedBluetoothDiscovery()
                // started discovering devices
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> startedBluetoothDiscovery()
                // scan state changed (is connectable/discoverable/none)
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> bluetoothScanModeChanged(
                    intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                )
                BluetoothConnectionService.INCOMING_MESSAGE -> receivedBluetoothMessage(
                    intent.getStringExtra(BluetoothConnectionService.EXTRA_MESSAGE)
                )
                BluetoothConnectionService.SOCKET_STATE_CHANGED -> bluetoothSocketStateChanged(
                    state = intent.getIntExtra(BluetoothConnectionService.EXTRA_SOCKET_STATE, BluetoothConnectionService.SOCKET_STATE_ERROR),
                    address = intent.getStringExtra(BluetoothConnectionService.EXTRA_DEVICE_ADDRESS)
                )
                // toggled bluetooth
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    bluetoothStateChanged(state)
                }
            }
        }
    }
}
