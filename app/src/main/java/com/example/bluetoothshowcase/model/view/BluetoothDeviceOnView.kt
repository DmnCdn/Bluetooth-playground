package com.example.bluetoothshowcase.model.view

data class BluetoothDeviceOnView(
    val name: String,
    val address: String,
) {
    var bonded: Boolean = false
    var expanded: Boolean = false

    override fun toString(): String {
        return "(name=$name, address=$address, bonded=$bonded)"
    }
}
