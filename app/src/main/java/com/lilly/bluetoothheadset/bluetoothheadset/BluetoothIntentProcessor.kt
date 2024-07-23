package com.lilly.bluetoothheadset.bluetoothheadset

import android.content.Intent

interface BluetoothIntentProcessor {

    fun getBluetoothDevice(intent: Intent): BluetoothDeviceWrapper?
}
