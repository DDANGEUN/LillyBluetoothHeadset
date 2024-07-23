package ai.ableai.bhs.util

import ai.ableai.bhs.MyApplication
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

interface PermissionsCheckStrategy {
    fun hasPermissions(): Boolean
    fun hasBluetoothConnectPermission(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun isBluetoothSupport(): Boolean
}
class DefaultPermissionsCheckStrategy : PermissionsCheckStrategy {
    private val context: Context = MyApplication.applicationContext()

    override fun isBluetoothSupport():Boolean{
        val bluetoothManager: BluetoothManager? = context.getSystemService(
            BluetoothManager::class.java)
        val mBluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        return mBluetoothAdapter != null
    }

    override fun isBluetoothEnabled():Boolean{
        val bluetoothManager: BluetoothManager? = MyApplication.applicationContext().getSystemService(
            BluetoothManager::class.java)
        val mBluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        // todo
        // https://developer.android.com/develop/connectivity/bluetooth/setup?hl=ko
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        return mBluetoothAdapter?.isEnabled != false
    }
    override fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // API 31 이하에서는 권한이 필요하지 않음
        }
    }
    override fun hasPermissions(): Boolean {
        return if (context.applicationInfo.targetSdkVersion <= android.os.Build.VERSION_CODES.R ||
            android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R
        ) {
            (PackageManager.PERMISSION_GRANTED == context.checkPermission(
                Manifest.permission.BLUETOOTH,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
            )&& ( PackageManager.PERMISSION_GRANTED == context.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
            )))
        } else {
            // for android 12/S or newer
            ( PackageManager.PERMISSION_GRANTED == context.checkPermission(
                Manifest.permission.BLUETOOTH_CONNECT,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
            )) && ( PackageManager.PERMISSION_GRANTED == context.checkPermission(
                Manifest.permission.BLUETOOTH_SCAN,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
            )&& ( PackageManager.PERMISSION_GRANTED == context.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
            )))
        }
    }
}
class PreviewPermissionChecker : PermissionsCheckStrategy {
    override fun hasPermissions(): Boolean = true
    override fun hasBluetoothConnectPermission(): Boolean = true
    override fun isBluetoothEnabled(): Boolean = true
    override fun isBluetoothSupport(): Boolean = true
}