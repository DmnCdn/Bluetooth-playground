package com.example.bluetoothshowcase.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val btAdapter: BluetoothAdapter
) : ViewModel() {
    private var _state = MutableLiveData<MainViewState>()
    val state: LiveData<MainViewState> = _state

    private val list: MutableList<BluetoothDevice> = mutableListOf()


    init {
        _state.value = MainViewState.Started
    }

    fun addDevice(device: BluetoothDevice) {
        list.add(device)
    }

    fun search() {
        _state.value = MainViewState.Loading
        list.clear()
    }

    fun searchFinished() {
        _state.value = MainViewState.Loaded(list)
    }

    fun isBluetoothEnabled() : Boolean = btAdapter.isEnabled
}