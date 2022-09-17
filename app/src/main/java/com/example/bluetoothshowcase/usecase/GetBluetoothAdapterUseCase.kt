package com.example.bluetoothshowcase.usecase

import android.bluetooth.BluetoothAdapter
import javax.inject.Inject

class GetBluetoothAdapterUseCase @Inject constructor(
    private val adapter: BluetoothAdapter
) {
    operator fun invoke() = adapter
}
