package ai.ableai.bhs.bluetoothheadset

import android.bluetooth.BluetoothDevice

interface BluetoothDeviceWrapper {
    val device: BluetoothDevice
    val name: String
    val deviceClass: Int?
    val address: String
    val type: Int
    val bondState: Int
}
