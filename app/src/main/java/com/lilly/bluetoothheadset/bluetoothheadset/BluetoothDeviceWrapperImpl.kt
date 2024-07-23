package com.lilly.bluetoothheadset.bluetoothheadset

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

internal const val DEFAULT_DEVICE_NAME = "Bluetooth"

@SuppressLint("MissingPermission")
data class BluetoothDeviceWrapperImpl(
    override val device: BluetoothDevice,
    override val name: String = device.name ?: DEFAULT_DEVICE_NAME,
    override val deviceClass: Int? = device.bluetoothClass?.deviceClass,
    override val address: String = device.address,
    override val type: Int = device.type,
    override val bondState: Int = device.bondState
) : BluetoothDeviceWrapper
