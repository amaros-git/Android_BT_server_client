package com.verifone.bluetooth.client

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.media.effect.EffectContext
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*


const val REQUEST_CONNECT_DEVICE_SECURE = 0

class MainActivity : AppCompatActivity() {

    private val logTag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //TODO Request LOCATION permission
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuItemBtSecureConnect -> {
                val intent = Intent(applicationContext, DeviceListActivity::class.java)
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE_SECURE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CONNECT_DEVICE_SECURE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val address: String? = data?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                    if (address != null) {
                        BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)?.let {
                            val dummyTransmitter = Thread(
                                Runnable {
                                    dummyDataExchange(it)
                                }).start()
                        }
                    } else {
                        Log.d(logTag, "EXTRA_DEVICE_ADDRESS is null")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Connector.cancel()
    }

    private fun connect(device: BluetoothDevice):BluetoothSocket? = runBlocking {
        Log.d(logTag, "Connecting to ${device.name}")
        val connector = Connector.connectAsync(device)
        connector.await()
    }

       /* Log.d(logTag, "Waiting on connection ...")
        val connector = GlobalScope.async(Dispatchers.Main) {
            val socket =
            if (socket != null) {
                Log.d(logTag, "Connected to ${socket.remoteDevice.name}")
                socket
            } else {
                Log.d(logTag, "Socket is null")
                null
                //TODO What else ) ?
            }
        }
    }

        Log.d(logTag, "Here")*/

        //dummy coroutine sending Hello

    private fun dummyDataExchange(device: BluetoothDevice) {
        val socket = connect(device)
        socket?.use {
            Log.d(logTag, "Connected to ${socket.remoteDevice.name}")
            runBlocking {
                var shouldExit = false
                while(!shouldExit) {
                    for (i in 1..3) {
                        try {
                            socket.outputStream.write("Hello".toByteArray())
                            Log.d(logTag, "Sent Hello")
                            delay(1000)
                        } catch (e: IOException) {
                            Log.d(logTag, "Write failed: ${e.message}")
                            shouldExit = true
                        }
                    }
                }
            }
            Connector.cancel()
        }
    }


    private object Connector {

        private val logTag = "Connector"

        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private var socket: BluetoothSocket? = null

        private var isConnected = false

        suspend fun connectAsync(device: BluetoothDevice) = coroutineScope {
            if(isConnected) {
                Connector.cancel()
            }

            async {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

                sppUUIDConnector(device)
               /* if (!isConnected) {
                    sppChannelConnector(device)
                }*/
                socket
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(socket)
        }

        private fun sppUUIDConnector(device: BluetoothDevice) {
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID) //TODO if NULL
                Log.d(logTag, "Created client socket")
            } catch (e: IOException) {
                Log.d(logTag, "Cannot create client socket")
                socket?.close()
                return
            }

            Log.d(logTag, "Connecting to ${device.name} ...")
            var retriesCount = 1
            do {
                try {
                    socket?.connect()
                    Log.d(logTag, "Connected to ${device.name}")
                    isConnected = true
                    break
                } catch (e: IOException) {
                    Log.d(logTag, "Cannot connect ${device.name}: ${e.message}")
                    retriesCount--
                    //Log.d(logTag, "retriesCount = $retriesCount")
                }
            } while (retriesCount > 0)

            if((retriesCount <= 0) && (!isConnected)) {
                cancel()
            }
        }

        private fun sppChannelConnector(device: BluetoothDevice) {
            val channel: IntRange = (0..64) //createRfcommSocket throws IOexception on channel 0 and 31
            for (ch in channel) {
                Log.d(logTag, "Trying channel $ch")
                try {
                    socket = device.javaClass.getMethod("createRfcommSocket", Int::class.java).invoke(device, ch) as BluetoothSocket
                    if (socket != null) {
                        socket?.connect()
                        Log.d(logTag, "Connected to ${device.name} on SPP channel $ch")
                        isConnected = true
                        break
                    }
                    continue
                } catch (e: Exception) {
                    if (e.cause is IOException) {
                        Log.d(logTag, "Exception occurred: ${(e.cause as IOException).message}")
                    }
                    //ignore other IOExceptions in this case
                    continue
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            Log.d(logTag, "Canceling connection")
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(logTag, "Could not close the client socket", e)
            }

            isConnected = false
            socket = null
        }
    }
}
