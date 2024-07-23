package com.lilly.bluetoothheadset

import com.lilly.bluetoothheadset.audio.AudioDeviceManager
import com.lilly.bluetoothheadset.ble.BleManager
import com.lilly.bluetoothheadset.bluetoothheadset.BluetoothHeadsetManager
import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.io.File


class MyApplication : Application() {

    init{
        instance = this
    }

    companion object {
        lateinit var instance: MyApplication
        fun applicationContext() : Context {
            return instance.applicationContext
        }
    }


    override fun onCreate() {
        super.onCreate()
        val dexOutputDir: File = codeCacheDir
        dexOutputDir.setReadOnly()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        AudioDeviceManager.initialize()
        BluetoothHeadsetManager.initialize()
        BleManager.initialize()
    }
}