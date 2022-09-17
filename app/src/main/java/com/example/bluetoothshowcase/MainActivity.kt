package com.example.bluetoothshowcase

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.bluetoothshowcase.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var navController: NavController

    private val viewModel by viewModels<ApplicationViewModel>()

    private val bluetoothBroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val message: String
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            message = getString(R.string.bluetooth_off)
                            Log.d(TAG, message)
                            showStatusError(message)
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            message = getString(R.string.bluetooth_turning_off)
                            Log.d(TAG, message)
                            showStatusError(message)
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            message = getString(R.string.bluetooth_turning_on)
                            Log.d(TAG, message)
                            showStatusMessage(message)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            message = getString(R.string.bluetooth_on)
                            Log.d(TAG, message)
                            hideStatus()
                        }
                    }
                }
            }
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment

        navController = navHostFragment.findNavController()

        setupPermissions()

        if (!viewModel.btAdapter.isEnabled) {
            showStatusError(getString(R.string.bluetooth_off))
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothBroadcastReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothBroadcastReceiver)
        super.onDestroy()
    }

    private fun setupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                )
            )
        }
        requestPermissions.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hideStatus() {
        with(binding.bluetoothInfoText) {
            if (isVisible) {
                isGone = true
            }
        }
    }

    private fun showStatusMessage(text: String) {
        with(binding.bluetoothInfoText) {
            if (isGone) {
                isVisible = true
            }
            setBackgroundColor(getColor(android.R.color.holo_green_light))
            setText(text)
        }
    }

    private fun showStatusError(text: String) {
        with(binding.bluetoothInfoText) {
            if (isGone) {
                isVisible = true
            }
            setBackgroundColor(getColor(R.color.error))
            setText(text)
        }
    }
}
