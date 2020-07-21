package com.verifone.bluetooth.client

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.log

const val EXTRA_DEVICE_ADDRESS = "device_address"

class DeviceListActivity: AppCompatActivity() {
    private val logTag = "DeviceListActivity"

    private var bluetoothAdapter: BluetoothAdapter? = null

    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>
    private lateinit var newDevicesArrayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_device_list)
        //setResult(Activity.RESULT_CANCELED)

        val scanButton = findViewById<View>(R.id.button_scan)
        scanButton.setOnClickListener {v ->
            doDiscovery()
            v.visibility = View.GONE
        }

        pairedDevicesArrayAdapter = ArrayAdapter(applicationContext, R.layout.device_name)
        newDevicesArrayAdapter = ArrayAdapter(applicationContext, R.layout.device_name)
        findViewById<ListView>(R.id.paired_devices).apply {
            adapter = pairedDevicesArrayAdapter
            onItemClickListener = deviceTextViewClickListener
        }
        findViewById<ListView>(R.id.new_devices).apply {
            adapter = newDevicesArrayAdapter
            onItemClickListener = deviceTextViewClickListener
        }

        var actionFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(broadcastReceiver, actionFilter)
        actionFilter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(broadcastReceiver, actionFilter)
        actionFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(broadcastReceiver, actionFilter)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null) {
            val pairedDevices = bluetoothAdapter!!.bondedDevices
            if (pairedDevices.size > 0) {
                findViewById<View>(R.id.title_paired_devices).visibility = View.VISIBLE
                for (device in pairedDevices) {
                    pairedDevicesArrayAdapter.add(createDeviceDisplayName(device))
                }
            }
            else {
                val noDevices: String = resources.getText(R.string.none_paired).toString()
                pairedDevicesArrayAdapter.add(noDevices)
            }
        }
        else {
            Log.d(logTag, "Bluetooth isn't available")
            Toast.makeText(applicationContext, "Bluetooth isn't available", Toast.LENGTH_LONG).show()
            finishActivity(Activity.RESULT_CANCELED, "")
        }
    }

    private fun finishActivity(activityResult: Int, address: String) {
        if(bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS, address)
        setResult(activityResult, intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        unregisterReceiver(broadcastReceiver)
    }

    private fun doDiscovery() {
        Log.d(logTag, "doDiscovery()")
        setTitle(R.string.scanning)

        findViewById<View>(R.id.title_new_devices).visibility = View.VISIBLE

        if(bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        bluetoothAdapter?.startDiscovery()
    }


    private val deviceTextViewClickListener = OnItemClickListener { _, view, _, _ ->
        val text = (view as TextView).text.toString()
        val address = text.substring(text.length - 17)

        val device = bluetoothAdapter?.getRemoteDevice(address)

        if (device != null) {
            when (device.bondState) {
                BluetoothDevice.BOND_NONE -> {
                    Toast.makeText(applicationContext, "Pairing ...", Toast.LENGTH_SHORT).show()
                    device.createBond() //Finish in BroadcastReceiver -> ACTION_BOND_STATE_CHANGED
                }

                BluetoothDevice.BOND_BONDING -> {
                    Log.d(logTag, "Cannot select device, pairing is in progress ...")
                }

                BluetoothDevice.BOND_BONDED -> {
                    finishActivity(Activity.RESULT_OK, address)
                }
                else -> Log.d(logTag, "Unsupported device bond state")
            }
        }
    }

    private fun createDeviceDisplayName(device: BluetoothDevice): String {
        return  """
                ${device.name}
                ${device.address}
                """.trimIndent()
    }


    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if ((device != null) && (device.name != null) && (device.bondState == BluetoothDevice.BOND_NONE)) {
                        val foundDevice = createDeviceDisplayName(device)
                        Log.d(logTag, "Found device: $foundDevice")

                        var isInTheList = false
                        for (i in 0 until newDevicesArrayAdapter.count) {
                            if (foundDevice == newDevicesArrayAdapter.getItem(i)) {
                                isInTheList = true;
                                break;
                            }
                        }
                        if (!isInTheList) {
                            newDevicesArrayAdapter.add(foundDevice)
                        }

                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0)

                    if (bondState == BluetoothDevice.BOND_BONDED) {//ignore others states
                        finishActivity(Activity.RESULT_OK, if (device != null) device.address else "")
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    setTitle(R.string.select_device)
                    if (newDevicesArrayAdapter.count == 0) {
                        newDevicesArrayAdapter.add(resources.getText(R.string.none_found).toString())
                    }
                }

            }
        }
    }
}