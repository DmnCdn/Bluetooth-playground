package com.example.bluetoothshowcase.main

import android.bluetooth.BluetoothDevice

sealed class MainViewState {
    object Started: MainViewState()
    object Loading: MainViewState()
    data class Loaded(val devices: List<BluetoothDevice>): MainViewState()
}