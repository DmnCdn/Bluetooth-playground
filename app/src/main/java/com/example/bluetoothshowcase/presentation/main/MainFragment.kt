package com.example.bluetoothshowcase.presentation.main

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.bluetoothshowcase.R
import com.example.bluetoothshowcase.databinding.DeviceListItemViewBinding
import com.example.bluetoothshowcase.databinding.FragmentMainBinding
import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView
import com.example.bluetoothshowcase.service.BluetoothConnectionService
import com.example.bluetoothshowcase.utils.BroadcastActionCallback
import com.example.bluetoothshowcase.utils.GpsUtils
import com.example.bluetoothshowcase.utils.MyBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "MainFragment"

@AndroidEntryPoint
class MainFragment : Fragment(), ClickActionInterface, BroadcastActionCallback {

    private var searchStarted = false

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private var mBluetoothConnection: BluetoothConnectionService? = null
    private var mBluetoothServiceBounded: Boolean = false

    private val gpsUtils by lazy {
        GpsUtils(requireActivity())
    }

    private val deviceAdapter by lazy {
        DeviceListAdapter(this)
    }

    private val receiver = MyBroadcastReceiver(this)

    private val bluetoothServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as BluetoothConnectionService.LocalBinder
            mBluetoothConnection = localBinder.service
            mBluetoothServiceBounded = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBluetoothConnection = null
            mBluetoothServiceBounded = false
        }
    }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                search()
            }
        }

    private var mLeScanCallback: ScanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let {
                    viewModel.addDevice(it.device)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(LocationManager.MODE_CHANGED_ACTION)
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireActivity().registerReceiver(receiver, filter)

        if(!mBluetoothServiceBounded) {
            bindBluetoothService()
        }
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
            if(searchStarted) {
                viewModel.searchFinished()
            } else {
                searchStarted = true
                search()
            }
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
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                MainViewState.Started -> {
                    displayText(requireContext().getString(R.string.search_for_devices))
                }
                MainViewState.Loading -> {
                    displayText(requireContext().getString(R.string.searching))
                    showProgress()
                    disableButton()
                }
                is MainViewState.Loaded -> {
                    displayText(
                        requireContext().getString(R.string.found_devices, state.devices.count())
                    )

                    try {
                        viewModel.btAdapter.cancelDiscovery()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "observeViewModel: ",e)
                    }
                    hideProgress()
                    enableButton()
                    deviceAdapter.submitList(state.devices.toList())
                }
            }
        }
    }

    private fun search() {
        if(!searchStarted){
            return
        }
        // check if bluetooth is on
        if (!viewModel.isBluetoothEnabled()) {
            turnOnBluetooth()
            return
        }
        // check if location is on
        if (!isLocationEnabled()) {
            gpsUtils.turnGPSOn()
            return
        }
        // check if has scan permissions can be done by a try catch block
        // by catching a SecurityException
        try {
            if(!viewModel.btAdapter.isDiscovering) {
                viewModel.btAdapter.startDiscovery()
                scanLeDevice(true)
                viewModel.search()
                deviceAdapter.submitList(emptyList())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "search: ", e)
            return
        } catch (e: Exception) {
            Log.e(TAG, "search: ", e)
            return
        }
    }

    private fun turnOnBluetooth() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestBluetooth.launch(intent)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
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

    private fun scanLeDevice(enable: Boolean) {
        try {
            when (enable) {
                true -> {
                    viewModel.btAdapter.bluetoothLeScanner?.startScan(mLeScanCallback)
                }
                else -> {
                    viewModel.btAdapter.bluetoothLeScanner?.stopScan(mLeScanCallback)
                }
            }
        } catch (e : SecurityException) {
            Log.e(TAG, "scanLeDevice: ", e)
            return
        }
    }

    private fun bindBluetoothService() {
        if(mBluetoothServiceBounded) {
            return
        }
        with(requireContext()) {
            val serviceIntent = Intent(this, BluetoothConnectionService::class.java)
            bindService(serviceIntent, bluetoothServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindBluetoothService() {
        if(!mBluetoothServiceBounded) {
            return
        }
        requireContext().unbindService(bluetoothServiceConnection)
    }

    private fun disableBluetoothScan() {
        try {
            viewModel.btAdapter.cancelDiscovery()
            scanLeDevice(false)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.e(TAG, "connectToDevice: ")
        mBluetoothConnection?.startConnectionClient(device)
    }

    // list adapter click actions

    override fun connectButtonClicked(device: BluetoothDeviceOnView) {
        Log.d(TAG, "${device.address} clicked!")
        try {
            viewModel.btAdapter.cancelDiscovery()
            val deviceObject = viewModel.getDeviceWithAddress(device.address)
            if(!viewModel.btAdapter.bondedDevices.contains(deviceObject)) {
                deviceObject.createBond()
            } else {
                connectToDevice(deviceObject)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "connectButtonClicked: ", e)
            return
        }
    }

    override fun itemViewClicked(itemViewBinding: DeviceListItemViewBinding, position: Int) {
        Log.d(TAG, "itemViewClicked: $position")
        with(itemViewBinding.searchButton) {
            isVisible = !isVisible
        }
        deviceAdapter.notifyItemChanged(position)
    }

    // methods for broadcast receiver callbacks

    override fun locationModeChanged() {
        search()
    }

    override fun bluetoothDeviceFound(device: BluetoothDevice?) {
        device?.let { viewModel.addDevice(it) }
    }

    override fun bluetoothBondStateChanged(device: BluetoothDevice?) {
        try {
            when (device?.bondState) {
                BluetoothDevice.BOND_NONE -> {
                    Log.i(TAG, "${device.address}: no bond")
                }
                BluetoothDevice.BOND_BONDING -> {
                    Log.i(TAG, "${device.address}: bonding")
                }
                BluetoothDevice.BOND_BONDED -> {
                    Log.i(TAG, "${device.address}: bonded")
                    connectToDevice(device)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "onReceive: ", e)
            return
        }
    }

    override fun startedBluetoothDiscovery() {
        Log.i(TAG, "onReceive: STARTED")
    }

    override fun finishedBluetoothDiscovery() {
        if (searchStarted) {
            searchStarted = false
            Log.i(TAG, "onReceive: FINISHED")
            scanLeDevice(false)
            viewModel.searchFinished()
        }
    }

    override fun bluetoothStateChanged(state: Int) {
        when(state) {
            BluetoothAdapter.STATE_TURNING_OFF -> {
                unbindBluetoothService()
                disableBluetoothScan()
            }
            BluetoothAdapter.STATE_ON -> {
                bindBluetoothService()
            }
        }
    }
}
