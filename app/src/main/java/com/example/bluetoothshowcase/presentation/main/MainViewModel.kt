package com.example.bluetoothshowcase.presentation.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bluetoothshowcase.mapper.BluetoothDeviceMapper
import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    val btAdapter: BluetoothAdapter,
    val deviceMapper: BluetoothDeviceMapper
) : ViewModel() {
    private var _state = MutableLiveData<MainViewState>()
    val state: LiveData<MainViewState> = _state

    private val bondedDevices: MutableSet<BluetoothDevice> = mutableSetOf()
    private val _list: MutableSet<BluetoothDeviceOnView> = mutableSetOf()
    val list: Set<BluetoothDeviceOnView>
        get() = _list

    init {
        _state.value = MainViewState.Started
    }

    fun updateBondedDevices() {
        try {
            bondedDevices.clear()
            bondedDevices.addAll(btAdapter.bondedDevices)
        } catch (e: SecurityException) {
            Log.e(TAG, "init: ", e)
        }
    }

    private fun sortDevices() {
        val newSet =
            _list.sortedWith(compareBy({ !it.bonded }, { it.name }, { it.address })).toSet()
        _list.apply {
            clear()
            addAll(newSet)
        }
    }

    fun addDevice(device: BluetoothDevice) {
        val deviceOnView = deviceMapper(device)
        if (device in bondedDevices) {
            Log.d(TAG, "addDevice: isBonded")
            deviceOnView.bonded = true
        }
        _list.add(deviceOnView)
    }

    fun search() {
        _list.clear()
        _state.value = MainViewState.Loading
    }

    fun searchFinished() {
        sortDevices()
        _state.value = MainViewState.Loaded(_list)
    }

    fun getDeviceWithAddress(
        address: String
    ): BluetoothDevice = btAdapter.getRemoteDevice(address)

    fun isBluetoothEnabled(): Boolean = btAdapter.isEnabled

    fun isBluetoothDiscoverable(): Boolean = (btAdapter.state == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
}
