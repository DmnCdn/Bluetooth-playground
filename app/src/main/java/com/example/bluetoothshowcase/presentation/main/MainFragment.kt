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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.bluetoothshowcase.R
import com.example.bluetoothshowcase.databinding.FragmentMainBinding
import com.example.bluetoothshowcase.model.view.BluetoothDeviceOnView
import com.example.bluetoothshowcase.service.BluetoothConnectionService
import com.example.bluetoothshowcase.utils.BroadcastActionCallback
import com.example.bluetoothshowcase.utils.GpsUtils
import com.example.bluetoothshowcase.utils.MyBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "MainFragment"
private const val DISCOVERABLE_DURATION = 15

@AndroidEntryPoint
class MainFragment : Fragment(), ClickActionInterface, BroadcastActionCallback {

    private var searchStarted = false
    private var isTurningOnDiscoverability = false

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private var mBluetoothConnection: BluetoothConnectionService? = null
    private var mBluetoothServiceBounded: Boolean = false

    private val gpsUtils by lazy { GpsUtils(requireActivity()) }

    private val deviceAdapter by lazy { DeviceListAdapter(this) }
    private var expandedDevice: BluetoothDeviceOnView? = null
    private var expandedPosition: Int? = null

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

    private val requestDiscoverability =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                updateScanModeState(viewModel.btAdapter.scanMode)
            } catch (e: SecurityException) {
                return@registerForActivityResult
            }

            if(result.resultCode != DISCOVERABLE_DURATION) {
                return@registerForActivityResult
            }
            mBluetoothConnection?.startAcceptThread()
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

    // lifecycle methods

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver()

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
        setupClickableViews()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // private methods

    private fun setupRecyclerView() {
        with(binding.deviceRecyclerView) {
            layoutManager = LinearLayoutManager(this.context)
            adapter = deviceAdapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    private fun setupClickableViews() {
        // since you cant turn off discoverability we'll just disable the switch after clicking yes
        // to turn on discoverability
        // after the timeout is over (we'll probably listen in a broadcast receiver), we flip the switch
        // and enable it again
        with(binding.discoverabilitySwitch) {
            setOnCheckedChangeListener { _, checked ->
                if(checked) {
                    // make the switch not clickable
//                    this.isClickable = false
                    // check if bluetooth enabled
                    // todo toggle off and check if bluetooth on
                    turnOnDiscoverability()
                } else {
//                    this.isClickable = true
                }
            }
        }

        binding.searchButton.setOnClickListener {
            searchStarted = true
            search()
        }

        binding.messageButton.setOnClickListener {
            mBluetoothConnection?.write("Hello there!")
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

    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        filter.addAction(LocationManager.MODE_CHANGED_ACTION)
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        requireActivity().registerReceiver(receiver, filter)

        val localFilter = IntentFilter()
        localFilter.addAction(BluetoothConnectionService.INCOMING_MESSAGE)
        localFilter.addAction(BluetoothConnectionService.SOCKET_STATE_CHANGED)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, localFilter)
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
        if (!viewModel.isBluetoothEnabled()) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(intent)
        }
    }

    private fun turnOnDiscoverability() {
        if(!viewModel.isBluetoothDiscoverable() && !isTurningOnDiscoverability) {
            isTurningOnDiscoverability = true
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION)
            requestDiscoverability.launch(intent)
        }
    }

    private fun toggleDiscoverabilitySwitch(checked: Boolean? = null) {
        with(binding.discoverabilitySwitch) {
            // flip the switch accordingly
            checked?.let {
                isChecked = checked
            } ?: run {
                isChecked = !isChecked
            }
        }
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

    private fun showConnectProgress(address: String?) {
        binding.connectingToText.text = requireContext().getString(R.string.connecting_to, address)
        binding.connectingView.isVisible = true
    }

    private fun hideConnectProgress() {
        binding.connectingView.isGone = true
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
            mBluetoothConnection?.stopService(serviceIntent)
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
        mBluetoothConnection?.startConnectionClient(device)
    }

    private fun updateScanModeState(state: Int) {
        when(state) {
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                toggleDiscoverabilitySwitch(checked = true)
            }
            else -> {
                toggleDiscoverabilitySwitch(checked = false)
                hideConnectProgress()
            }
        }
    }

    private fun toast(message: String?) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // list adapter click actions

    override fun connectButtonClicked(device: BluetoothDeviceOnView) {
        try {
            showConnectProgress(device.address)

            viewModel.btAdapter.cancelDiscovery()
            val deviceObject = viewModel.getDeviceWithAddress(device.address)
            if(deviceObject.bondState == BluetoothDevice.BOND_NONE) {
                deviceObject.createBond()
            } else {
                connectToDevice(deviceObject)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "connectButtonClicked: ", e)
            return
        }
    }

    override fun itemViewClicked(deviceOnView: BluetoothDeviceOnView, position: Int) {
        with(deviceOnView) {
            // check if was clicked on the same one
            if(expandedDevice == deviceOnView) {
                expandedDevice = null
                expandedPosition = null
                this.expanded = false
            } else {
                // expand clicked item
                deviceOnView.expanded = true

                // collapse the previously expanded item
                expandedDevice?.expanded = false
                expandedDevice = deviceOnView

                // notify the previous item changed and override the expanded position
                expandedPosition?.let { deviceAdapter.notifyItemChanged(it) }
                expandedPosition = position
            }
        }
        // notify the clicked item changed
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

    override fun bluetoothScanModeChanged(state: Int) {
        updateScanModeState(state)
    }

    override fun receivedBluetoothMessage(message: String?) {
        Log.e(TAG, "receivedBluetoothMessage: $message")
        toast(message)
    }

    override fun bluetoothSocketStateChanged(state: Int, address: String?) {
        when(state) {
            BluetoothConnectionService.SOCKET_STATE_CONNECTED -> {
                hideConnectProgress()
                binding.messageButton.isEnabled = true
                toast("Connected to $address")
            }
            BluetoothConnectionService.SOCKET_STATE_ACCEPT -> {
                binding.messageButton.isEnabled = false
                hideConnectProgress()
            }
            else -> {
                binding.messageButton.isEnabled = false
            }
        }
    }

    override fun bluetoothStateChanged(state: Int) {
        when(state) {
            BluetoothAdapter.STATE_TURNING_OFF -> {
                unbindBluetoothService()
                disableBluetoothScan()
                mBluetoothConnection?.clearThreads()
            }
            BluetoothAdapter.STATE_ON -> {
                bindBluetoothService()
            }
        }
    }
}
