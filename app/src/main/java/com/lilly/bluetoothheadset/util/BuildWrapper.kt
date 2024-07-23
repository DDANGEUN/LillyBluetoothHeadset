package com.lilly.bluetoothheadset.util

import android.os.Build

class BuildWrapper {
    fun getVersion(): Int = Build.VERSION.SDK_INT
}
