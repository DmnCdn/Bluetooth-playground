package com.example.bluetoothshowcase.main

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothshowcase.databinding.DeviceListItemViewBinding
import com.example.bluetoothshowcase.utils.viewBinding

private const val TAG = "DeviceListAdapter"

class DeviceListAdapter : ListAdapter<BluetoothDevice, DeviceViewHolder>(DeviceDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = parent.viewBinding(DeviceListItemViewBinding::inflate, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        try {
            holder.bind(getItem(position))
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, e.message.toString())
        }
    }
}

class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
    override fun areItemsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean =
        oldItem.address == newItem.address

    override fun areContentsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean =
        oldItem == newItem
}

class DeviceViewHolder(
    private val binding: DeviceListItemViewBinding
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(device: BluetoothDevice) = with(binding) {
        deviceAddressTextView.text = device.address
        if (ActivityCompat.checkSelfPermission(
                this.root.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        deviceNameTextView.text = device.name
    }
}