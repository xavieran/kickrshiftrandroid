/*
 * Copyright 2024 Emman1uel Jacyna <xavieran.lives@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xavieran.kickrshiftrandroid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.xavieran.kickrshiftrandroid.ble.ConnectionEventListener
import com.xavieran.kickrshiftrandroid.ble.ConnectionManager
import com.xavieran.kickrshiftrandroid.ble.ConnectionManager.parcelableExtraCompat
import com.xavieran.kickrshiftrandroid.ble.printProperties
import com.xavieran.kickrshiftrandroid.ble.toHexString
import com.xavieran.kickrshiftrandroid.databinding.ActivityShifterBinding
import timber.log.Timber
import java.util.UUID

class ShifterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShifterBinding
    private val device: BluetoothDevice by lazy {
        intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")
    }
    private val scaleFactor : Float = 1.1F
    private val defaultWheelSize = 21400
    private var wheelSize: Int = defaultWheelSize
    private var pendingWheelSize: Int = 0

    private val notifyingCharacteristics = mutableListOf<UUID>()
    private lateinit var wheelSizeCharacteristic: BluetoothGattCharacteristic
    private var waitingForResponse : Boolean = false

    @SuppressLint("SetTextI18n", "DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)

        binding = ActivityShifterBinding.inflate(layoutInflater)

        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.virtual_shifter)
        }

        updateText()

        binding.shiftUpButton.setOnClickListener {
            shiftUp()
        }
        binding.neutralButton.setOnClickListener {
            resetWheelSize()
        }
        binding.shiftDownButton.setOnClickListener {
            shiftDown()
        }

        populateWheelSizeCharacteristic()
    }

    private fun shiftUp() {
        var newWheelSize = (wheelSize.toFloat() * scaleFactor).toInt()
        if (newWheelSize > 65535)
        {
            newWheelSize = 65535
        }
        setWheelSize(newWheelSize)
    }

    private fun shiftDown() {
        var newWheelSize = (wheelSize.toFloat() / scaleFactor).toInt()
        if (newWheelSize <= 0)
        {
            newWheelSize = 1
        }
        setWheelSize(newWheelSize)
    }

    private fun resetWheelSize() {
        setWheelSize(defaultWheelSize)
    }

    private fun setWheelSize(size: Int) {
        log("Setting wheel size from: ${wheelSize} to ${size}")
        pendingWheelSize = size
        sendWheelSize(pendingWheelSize)
    }

    private fun updateText(){
        runOnUiThread {
            binding.wheelSizeTextView.text = "Wheel Size: " + String.format("%.3f", wheelSize.toFloat() / 10000)
        }
    }

    private fun sendWheelSize(size: Int){
        if (wheelSizeCharacteristic != null) {
            val newWheelSize : ByteArray =
                byteArrayOf(0x12) + byteArrayOf(size.toByte(), size.shr(8).toByte())
            ConnectionManager.writeCharacteristic(device, wheelSizeCharacteristic, newWheelSize)
            waitingForResponse = true
            disableUI();
        }
    }

    private fun characteristicChanged(uuid: UUID?, value: ByteArray) {
        if (uuid == wheelSizeCharacteristic.uuid)
        {
            Timber.i("Wheel size changed $value")
            waitingForResponse = false
            enableUI();
            wheelSize = pendingWheelSize
            updateText()
        }
    }

    private fun enableUI() {
        runOnUiThread {
            binding.shiftDownButton.isEnabled = true
            binding.shiftUpButton.isEnabled = true
            binding.neutralButton.isEnabled = true
        }
    }

    private fun disableUI() {
        runOnUiThread {
            binding.shiftDownButton.isEnabled = false
            binding.shiftUpButton.isEnabled = false
            binding.neutralButton.isEnabled = false
        }
    }

    private fun populateWheelSizeCharacteristic() {
        val fitnessMachineControlPoint = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
        val fitnessMachineService = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val service = ConnectionManager.deviceGattMap[device]?.getService(fitnessMachineService)
        if (service == null)
        {
            Timber.w("Wahoo Service doesn't exist")
            return
        }

        service.characteristics.forEach { it ->
            Timber.w("Characteristics ${it.uuid} ${it.printProperties()}")
            it.descriptors.forEach {
                Timber.w("  Descriptor: ${it.uuid} ${it.printProperties()}")
            }
        }
        wheelSizeCharacteristic = service.getCharacteristic(fitnessMachineControlPoint)
        if (wheelSizeCharacteristic == null)
        {
            Timber.w("No wheel circumference")
        }

        ConnectionManager.enableNotifications(device, wheelSizeCharacteristic)
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        Timber.i(message)
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    AlertDialog.Builder(this@ShifterActivity)
                        .setTitle("Disconnected")
                        .setMessage("Disconnected from device.")
                        .setPositiveButton("OK") { _, _ -> onBackPressed() }
                        .show()
                }
            }

            onCharacteristicRead = { _, characteristic, value ->
                log("Read from ${characteristic.uuid}: ${value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                log("Wrote to ${characteristic.uuid}")
            }

            onMtuChanged = { _, mtu ->
                log("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                log("Value changed on ${characteristic.uuid}: ${value.toHexString()}")
                characteristicChanged(characteristic.uuid, value)
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

}
