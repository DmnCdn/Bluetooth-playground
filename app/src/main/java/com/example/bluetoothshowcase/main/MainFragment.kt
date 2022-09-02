package com.example.bluetoothshowcase.main

import android.Manifest
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.example.bluetoothshowcase.databinding.FragmentMainBinding
import dagger.hilt.android.AndroidEntryPoint


private const val TAG = "MainFragment"

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private val deviceAdapter by lazy {
        DeviceListAdapter()
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive: ${intent.action}")
            val action = intent.action

            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.let {
                            Log.e(TAG, "onReceive: FOUND -> ${it.name} (${it.address})")
                            viewModel.addDevice(it)
                        }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.e(TAG, "onReceive: FINISHED")
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "onReceive: no scan permission")
                        return
                    }
                    viewModel.searchFinished()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.e(TAG, "onReceive: STARTED")
                }
            }
        }
    }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                search()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        requireActivity().registerReceiver(receiver, filter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.searchButton.setOnClickListener {
            search()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        with(binding.deviceRecyclerView) {
            layoutManager = LinearLayoutManager(this.context)
            adapter = deviceAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                MainViewState.Started -> {
                    displayText("Search for nearby bluetooth devices.")
                }
                MainViewState.Loading -> {
                    displayText("Searching...")
                    showProgress()
                    disableButton()
                }
                is MainViewState.Loaded -> {
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "observeViewModel: no scan permission")
                    } else {
                        displayText("Found ${state.devices.count()} devices near you.")
                        viewModel.btAdapter.cancelDiscovery()
                        hideProgress()
                        enableButton()
                        deviceAdapter.submitList(state.devices)
                    }
                }
            }
        }
    }

    private fun search() {
        if (viewModel.isBluetoothEnabled()) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "search: No scan permission.")
                return
            }
            viewModel.btAdapter.startDiscovery()
            viewModel.search()
        } else {
            turnOnBluetooth()
        }
    }

    private fun turnOnBluetooth() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestBluetooth.launch(intent)
    }

    private fun displayText(message: String) {
        binding.infoTextView.text = message
    }

    private fun showProgress() {
        binding.loadingView.isInvisible = false
    }

    private fun hideProgress() {
        binding.loadingView.isInvisible = true
    }

    private fun disableButton() {
        binding.searchButton.isEnabled = false
    }

    private fun enableButton() {
        binding.searchButton.isEnabled = true
    }
}