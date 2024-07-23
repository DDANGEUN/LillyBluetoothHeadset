package com.lilly.bluetoothheadset.bluetoothheadset

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.EXTRA_DEVICE
import android.content.Intent

class BluetoothIntentProcessorImpl : BluetoothIntentProcessor {

    override fun getBluetoothDevice(intent: Intent): BluetoothDeviceWrapper? =
        intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
            ?.let { device -> BluetoothDeviceWrapperImpl(device) }
}
