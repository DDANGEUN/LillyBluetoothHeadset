package com.lilly.bluetoothheadset.util

import com.lilly.bluetoothheadset.MyApplication
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset

const val TAG = "Utils"

class Utils {
    companion object{
        fun LifecycleOwner.repeatOnStarted(block: suspend CoroutineScope.() -> Unit) {
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
            }
        }
        fun showNotification(msg: String) {
            Toast.makeText(MyApplication.applicationContext(), msg, Toast.LENGTH_SHORT).show()
        }

        fun requestEnableBluetooth(requestEnableBleResult: ActivityResultLauncher<Intent>) {
            val bleEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBleResult.launch(bleEnableIntent)
        }

        fun hasBluetoothPermissions(): Boolean {
            // 권한 확인 로직
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = MyApplication.applicationContext()
                val bluetoothConnectPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                val bluetoothScanPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

                bluetoothConnectPermission && bluetoothScanPermission
            } else {
                true // API 31 이하에서는 권한이 필요하지 않음
            }
        }

        fun copyDataDir(dataDir: String): String {
            Log.i(com.lilly.bluetoothheadset.viewmodel.TAG, "data dir is $dataDir")
            copyAssets(dataDir)

            val newDataDir = MyApplication.applicationContext().getExternalFilesDir(null)!!.absolutePath
            Log.i(com.lilly.bluetoothheadset.viewmodel.TAG, "newDataDir: $newDataDir")
            return newDataDir
        }
        fun copyAssets(path: String) {
            val assets: Array<String>?
            try {
                assets = MyApplication.applicationContext().assets.list(path)
                if (assets!!.isEmpty()) {
                    copyFile(path)
                } else {
                    val fullPath = "${MyApplication.applicationContext().getExternalFilesDir(null)}/$path"
                    val dir = File(fullPath)
                    dir.mkdirs()
                    for (asset in assets.iterator()) {
                        val p: String = if (path == "") "" else path + "/"
                        copyAssets(p + asset)
                    }
                }
            } catch (ex: IOException) {
                Log.e(com.lilly.bluetoothheadset.viewmodel.TAG, "Failed to copy $path. $ex")
            }
        }
        fun copyFile(filename: String) {
            try {
                val istream = MyApplication.applicationContext().assets.open(filename)
                val newFilename = MyApplication.applicationContext().getExternalFilesDir(null).toString() + "/" + filename
                val ostream = FileOutputStream(newFilename)
                // Log.i(TAG, "Copying $filename to $newFilename")
                val buffer = ByteArray(1024)
                var read = 0
                while (read != -1) {
                    ostream.write(buffer, 0, read)
                    read = istream.read(buffer)
                }
                istream.close()
                ostream.flush()
                ostream.close()
            } catch (ex: Exception) {
                Log.e(com.lilly.bluetoothheadset.viewmodel.TAG, "Failed to copy $filename, $ex")
            }
        }

        fun stringToByteArray(input: String): ByteArray {
            return input.toByteArray(Charset.defaultCharset())
        }

    }
}