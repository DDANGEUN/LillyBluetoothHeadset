package com.lilly.bluetoothheadset.bluetoothheadset

import com.lilly.bluetoothheadset.util.SystemClockWrapper
import android.os.Handler
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.concurrent.TimeoutException

internal const val TIMEOUT = 5000L
private const val TAG = "BluetoothScoJob"

abstract class BluetoothScoJob(
    private val bluetoothScoHandler: Handler,
    private val systemClockWrapper: SystemClockWrapper
) {



    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var bluetoothScoRunnable: BluetoothScoRunnable? = null

    protected abstract fun scoAction()

    open fun scoTimeOutAction() {}

    @Synchronized
    fun executeBluetoothScoJob() {
        // cancel existing runnable
        bluetoothScoRunnable?.let { bluetoothScoHandler.removeCallbacks(it) }

        BluetoothScoRunnable().apply {
            bluetoothScoRunnable = this
            bluetoothScoHandler.post(this)
        }
        Log.d(TAG, "Scheduled bluetooth sco job")

    }

    @Synchronized
    fun cancelBluetoothScoJob() {
        bluetoothScoRunnable?.let {
            bluetoothScoHandler.removeCallbacks(it)
            bluetoothScoRunnable = null
            Log.d(TAG, "Canceled bluetooth sco job")
        }
    }

    inner class BluetoothScoRunnable : Runnable {

        private val startTime = systemClockWrapper.elapsedRealtime()
        private var elapsedTime = 0L

        override fun run() {
            if (elapsedTime < TIMEOUT) {
                scoAction()
                elapsedTime = systemClockWrapper.elapsedRealtime() - startTime
                bluetoothScoHandler.postDelayed(this, 500)
            } else {
                Log.e(TAG, "Bluetooth sco job timed out", TimeoutException())
                scoTimeOutAction()
                cancelBluetoothScoJob()
            }
        }
    }
}
