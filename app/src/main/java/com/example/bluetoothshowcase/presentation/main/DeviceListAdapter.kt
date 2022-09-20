package com.example.bluetoothshowcase.presentation.main

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothshowcase.R
import com.example.bluetoothshowcase.databinding.DeviceListItemViewBinding
import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView
import com.example.bluetoothshowcase.utils.viewBinding

private const val TAG = "DeviceListAdapter"

interface ClickActionInterface {
    fun connectButtonClicked(device: BluetoothDeviceOnView)
    fun itemViewClicked(deviceOnView: BluetoothDeviceOnView, position: Int)
}

class DeviceListAdapter (
    private val clickAction: ClickActionInterface
) : ListAdapter<BluetoothDeviceOnView, DeviceViewHolder>(DeviceDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = parent.viewBinding(DeviceListItemViewBinding::inflate, false)
        return DeviceViewHolder(binding, clickAction)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        try {
            holder.bind(getItem(position))
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, e.message.toString())
        }
    }
}

class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDeviceOnView>() {
    override fun areItemsTheSame(
        oldItem: BluetoothDeviceOnView,
        newItem: BluetoothDeviceOnView
    ): Boolean =
        oldItem.address == newItem.address

    override fun areContentsTheSame(
        oldItem: BluetoothDeviceOnView,
        newItem: BluetoothDeviceOnView
    ): Boolean =
        oldItem == newItem
}

class DeviceViewHolder(
    private val binding: DeviceListItemViewBinding,
    private val onClickAction: ClickActionInterface
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(device: BluetoothDeviceOnView) = with(binding) {
        if(device.bonded) {
            binding.starImageView.isVisible = true
        }
        deviceAddressTextView.text = device.address
        deviceNameTextView.text = device.name

        searchButton.isVisible = device.expanded

        searchButton.setOnClickListener {
            onClickAction.connectButtonClicked(device)
        }
        itemView.setOnClickListener {
            onClickAction.itemViewClicked(device, adapterPosition)
        }
    }
}
