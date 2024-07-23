package ai.ableai.bhs

import ai.ableai.bhs.audio.AudioDeviceManager
import ai.ableai.bhs.ble.BleManager
import ai.ableai.bhs.bluetoothheadset.BluetoothHeadsetManager
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