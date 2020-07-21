package com.verifone.bluetooth.server

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.*
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private val logTag = "MainActivity"

    private var acceptThread: AcceptThread? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        acceptThread = AcceptThread()
        acceptThread?.start()
    }


    private inner class AcceptThread : Thread() {

        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private var mmServerSocket: BluetoothServerSocket? = null

        override fun run() {
            try {
                mmServerSocket = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord("Server", MY_UUID)
                Log.d(logTag, "Created server socket")
            } catch (e: IOException) {
                Log.d(logTag, "Cannot create server socket")
                return
            }

            // Keep listening until exception occurs or a socket is returned.
            Log.d(logTag, "Listening ...")
            val mSocket = try {
                mmServerSocket?.accept()
            } catch (e: IOException) {
                Log.e(logTag, "Socket's accept() method failed", e)
                null
            }

            mSocket?.also {
                Log.d(logTag, "Created connection with ${mSocket.remoteDevice.name}")
                mmServerSocket?.close()

                var shouldLoop = true
                var bufIn = ByteArray(1024)
                while (shouldLoop) {
                    try {
                        Log.d(logTag, "Waiting on data ...")
                        mSocket.inputStream.read(bufIn)
                        val answer = String(bufIn, 0, 5)
                        Log.d(logTag, "Read is: $answer")
                        sleep(1000)
                    } catch (e: IOException) {
                        Log.d(logTag, "Read failed: ${e.message}")
                        shouldLoop = false
                    }
                }
            }


        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(logTag, "Could not close the connect socket", e)
            }
        }
    }

}