package com.example.bluetoothshowcase.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.bluetoothshowcase.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

private const val TAG = "xdService"
private const val GenericFileTransferServiceClassUUID = "00001202-0000-1000-8000-00805F9B34FB"

@AndroidEntryPoint
class XDService(
    private val btAdapter: BluetoothAdapter
) : Service() {

    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    private var mConnectedThread: ConnectedThread? = null

    override fun onCreate() {
        super.onCreate()
        start()
    }

    init {
        startService(Intent(applicationContext, XDService::class.java))
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread : Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?
        override fun run() {
            Log.d(TAG, "run: AcceptThread Running.")
            var socket: BluetoothSocket? = null
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(
                    TAG,
                    "run: RFCOM server socket start....."
                )
                socket = mmServerSocket?.accept()
                Log.d(
                    TAG,
                    "run: RFCOM server socket accepted connection."
                )
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "AcceptThread: IOException: " + e.message
                )
            }

            //talk about this is in the 3rd
            socket?.let { connected(it, mmDevice) }
            Log.i(TAG, "END mAcceptThread ")
        }

        fun cancel() {
            Log.d(TAG, "cancel: Canceling AcceptThread.")
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "cancel: Close of AcceptThread ServerSocket failed. " + e.message
                )
            }
        }

        init {
            var tmp: BluetoothServerSocket? = null

            // Create a new listening server socket
            try {
                tmp = btAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    applicationContext.getString(R.string.app_name),
                    UUID.fromString(GenericFileTransferServiceClassUUID)
                )
                Log.d(
                    TAG,
                    "AcceptThread: Setting up Server using: $GenericFileTransferServiceClassUUID"
                )
            } catch (e: SecurityException) {
                Log.e(
                    TAG,
                    "AcceptThread: IOException: " + e.message
                )
            }
            mmServerSocket = tmp
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(device: BluetoothDevice?, uuid: UUID?) :
        Thread() {
        private var mmSocket: BluetoothSocket? = null
        override fun run() {
            var tmp: BluetoothSocket? = null
            Log.i(TAG, "RUN mConnectThread ")

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(
                    TAG,
                    "ConnectThread: Trying to create InsecureRfcommSocket using UUID: $GenericFileTransferServiceClassUUID"
                )
                tmp = mmDevice?.createRfcommSocketToServiceRecord(deviceUUID)
            } catch (e: SecurityException) {
                Log.e(
                    TAG,
                    "ConnectThread: Could not create InsecureRfcommSocket " + e.message
                )
            }
            mmSocket = tmp

            // Make a connection to the BluetoothSocket
            try {
                // Always cancel discovery because it will slow down a connection
                btAdapter.cancelDiscovery()
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket?.connect()
                Log.d(TAG, "run: ConnectThread connected.")
            } catch (e: SecurityException) {
                // Close the socket
                try {
                    mmSocket?.close()
                    Log.d(TAG, "run: Closed Socket.")
                } catch (e1: IOException) {
                    Log.e(TAG, "mConnectThread: run: Unable to close connection in socket " + e1.message)
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: $GenericFileTransferServiceClassUUID")
            }

            //will talk about this in the 3rd video
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.")
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "cancel: close() of mmSocket in Connectthread failed. " + e.message
                )
            }
        }

        init {
            Log.d(TAG, "ConnectThread: started.")
            mmDevice = device
            deviceUUID = uuid
        }
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread?.cancel()
            mConnectThread = null
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread()
            mInsecureAcceptThread?.start()
        }
    }

    /**
     * AcceptThread starts and sits waiting for a connection.
     * Then ConnectThread starts and attempts to make a connection with the other devices AcceptThread.
     */
    fun startClient(device: BluetoothDevice?, uuid: UUID?) {
        Log.d(TAG, "startClient: Started.")

        mConnectThread = ConnectThread(device, uuid)
        mConnectThread?.start()
    }

    /**
     * Finally the ConnectedThread which is responsible for maintaining the BTConnection, Sending the data, and
     * receiving incoming data through input/output streams respectively.
     */
    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            val buffer = ByteArray(1024) // buffer store for the stream
            var bytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                // Read from the InputStream
                try {
                    mmInStream?.let {
                        bytes = it.read(buffer)
                        val incomingMessage = String(buffer, 0, bytes)
                        Log.d(
                            TAG,
                            "InputStream: $incomingMessage"
                        )
                    }
                } catch (e: IOException) {
                    Log.e(
                        TAG,
                        "write: Error reading Input Stream. " + e.message
                    )
                    break
                }
            }
        }

        //Call this from the main activity to send data to the remote device
        fun write(bytes: ByteArray?) {
            try {
                val text = bytes?.let { String(it, Charset.defaultCharset()) }
                Log.d(
                    TAG,
                    "write: Writing to outputstream: $text"
                )
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "write: Error writing to output stream. " + e.message
                )
            }
        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }
        }

        init {
            Log.d(TAG, "ConnectedThread: Starting.")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    private fun connected(mmSocket: BluetoothSocket?, mmDevice: BluetoothDevice?) {
        Log.d(TAG, "connected: Starting.")

        // Start the thread to manage the connection and perform transmissions
        mmSocket?.let {
            mConnectedThread = ConnectedThread(it)
            mConnectedThread?.start()
        }
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        var r: ConnectedThread

        // Synchronize a copy of the ConnectedThread
        Log.d(TAG, "write: Write Called.")
        //perform the write
        mConnectedThread?.write(out)
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d(TAG, "onBind()")
        return null
    }
}
