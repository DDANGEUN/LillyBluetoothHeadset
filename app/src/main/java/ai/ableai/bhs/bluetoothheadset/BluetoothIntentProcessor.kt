package ai.ableai.bhs.bluetoothheadset

import android.content.Intent

interface BluetoothIntentProcessor {

    fun getBluetoothDevice(intent: Intent): BluetoothDeviceWrapper?
}
