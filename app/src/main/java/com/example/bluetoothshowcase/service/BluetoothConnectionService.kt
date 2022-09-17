package com.example.bluetoothshowcase.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.example.bluetoothshowcase.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject

private const val TAG = "BluetoothConnectionService"
private const val SerialPortServiceClassUUID = "00001101-0000-1000-8000-00805F9B34FB"

@AndroidEntryPoint
class BluetoothConnectionService : Service() {

    @Inject
    lateinit var bluetoothAdapter: BluetoothAdapter

    private var mAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    private var mDevice: BluetoothDevice? = null
    private var mDeviceUUID: UUID? = null

    private val localBinder = LocalBinder()

    // service lifecycle methods

    init {
        Log.i(TAG, "init")
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()
        startAcceptThread()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder {
        Log.i(TAG, "onBind")
        return localBinder
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    // threads

    // accept thread
    private inner class AcceptThread : Thread() {
        private val mServerSocket: BluetoothServerSocket?

        init {
            var tempSocket: BluetoothServerSocket? = null
            try {
                tempSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    applicationContext.getString(R.string.app_name),
                    UUID.fromString(SerialPortServiceClassUUID)
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Accept thread exception: ", e)
            }
            mServerSocket = tempSocket
        }

        override fun run() {
            Log.i(TAG, "run: accept thread")
            var socket: BluetoothSocket? = null
            try {
                socket = mServerSocket?.accept()
                Log.i(TAG, "run: server socket accepted")
            } catch (e: IOException) {
                Log.e(TAG, "Accept thread run exception: ", e)
            }
            socket?.let { connected(it) }
        }

        fun cancel() {
            Log.i(TAG, "cancel: accept thread")
            try {
                mServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Accept thread cancel exception: ", e)
            }
        }
    }

    // connect thread
    private inner class ConnectThread(device: BluetoothDevice?, uuid: UUID?) : Thread() {
        private var mSocket: BluetoothSocket? = null

        init {
            Log.i(TAG, "Connect thread init")
            mDevice = device
            mDeviceUUID = uuid
        }

        override fun run() {
            Log.i(TAG, "Connect thread run")
            var tempSocket: BluetoothSocket? = null

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tempSocket = mDevice?.createRfcommSocketToServiceRecord(mDeviceUUID)
            } catch (e: SecurityException) {
                Log.e(TAG, "Connect thread run: ", e)
            }
            mSocket = tempSocket

            // make a connection to the bluetooth socket
            try {
                bluetoothAdapter.cancelDiscovery()
                mSocket?.connect()
                Log.i(TAG, "run: ConnectThread connected.")
            } catch (e: SecurityException) {
                try {
                    mSocket?.close()
                    Log.i(TAG, "run: Closed Socket.")
                } catch (e1: IOException) {
                    Log.e(TAG, "mConnectThread: run: Unable to close connection in socket " + e1.message)
                }
                Log.i(TAG, "Connect thread run: Could not connect to device with UUID: $SerialPortServiceClassUUID")
            } catch (e: IOException) {
                Log.e(TAG, "mConnectThread run: ", e)
            }

            connected(mSocket)
        }

        fun cancel() {
            try {
                Log.i(TAG, "Connect thread: closing client socket")
                mSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Connect thread cancel: ", e)
            }
        }
    }

    // connected thread
    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket
        private var mmInStream: InputStream = socket.inputStream
        private var mmOutStream: OutputStream = socket.outputStream
        private val mmBuffer = ByteArray(1024)

        init {
            Log.i(TAG, "Connected thread init ${socket.remoteDevice.address}")
            mmSocket = socket
        }

        override fun run() {
            var numBytes: Int

            while (true) {
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Connected thread cancel: ", e)
            }
        }
    }

    // binder

    inner class LocalBinder : Binder() {
        internal val service: BluetoothConnectionService
            get() = this@BluetoothConnectionService
    }

    // public service methods

    // cancel connect thread and start the accept one
    // basically delete the previous connection and start discovery for new one
    private fun startAcceptThread() {
        if (mConnectThread != null) {
            mConnectThread?.cancel()
            mConnectThread = null
        }
        if (mAcceptThread == null) {
            mAcceptThread = AcceptThread()
            mAcceptThread?.start()
        }
    }

    // start connection with a device
    fun startConnectionClient(device: BluetoothDevice?) {
        Log.i(TAG, "startConnectionClient")
        // connect thread attempts to make a connection with the other devices accept thread
        mConnectThread = ConnectThread(device, UUID.fromString(SerialPortServiceClassUUID))
        mConnectThread?.start()
    }

    fun connected(socket: BluetoothSocket?) {
        Log.i(TAG, "connected")

        // Start the thread to manage the connection and perform transmissions
        socket?.let {
            mConnectedThread = ConnectedThread(it)
            mConnectedThread?.start()
        }
    }
}
