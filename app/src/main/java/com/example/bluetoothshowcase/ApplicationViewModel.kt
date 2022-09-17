package com.example.bluetoothshowcase

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ApplicationViewModel @Inject constructor(
    val btAdapter: BluetoothAdapter
): ViewModel()
