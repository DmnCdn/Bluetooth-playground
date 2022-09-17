package com.example.bluetoothshowcase.presentation.main

import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView

sealed class MainViewState {
    object Started: MainViewState()
    object Loading: MainViewState()
    data class Loaded(val devices: Set<BluetoothDeviceOnView>): MainViewState()
}