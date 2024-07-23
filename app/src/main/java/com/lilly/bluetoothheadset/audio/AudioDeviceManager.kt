package com.lilly.bluetoothheadset.audio

import com.lilly.bluetoothheadset.MyApplication
import com.lilly.bluetoothheadset.util.BuildWrapper
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class AudioDeviceManager {
    private val TAG = "AudioDeviceManager"
    private val audioManager: AudioManager = MyApplication.applicationContext()
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val build: BuildWrapper =
        BuildWrapper()
    private val audioFocusRequest: AudioFocusRequestWrapper =
        AudioFocusRequestWrapper()
    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        //TODO("Not yet implemented")
    }

    private var savedAudioMode = 0
    private var savedIsMicrophoneMuted = false
    private var savedSpeakerphoneEnabled = false
    private var audioRequest: AudioFocusRequest? = null

    companion object {
        private var INSTANCE: AudioDeviceManager? = null

        fun initialize(){
            if (INSTANCE == null){
                INSTANCE = AudioDeviceManager()
            }
        }

        fun get(): AudioDeviceManager {
            return INSTANCE ?:
            throw IllegalStateException("AudioDeviceManager must be initialized")
        }
    }

    fun hasEarpiece(): Boolean {
        val hasEarpiece = MyApplication.applicationContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY
        )
        if (hasEarpiece) {
            Log.d(TAG, "Earpiece available")
        }
        return hasEarpiece
    }



    @RequiresApi(Build.VERSION_CODES.S)
    fun test() {
        if (!MyApplication.applicationContext().packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.d(TAG, "오디오 출력 기능이 없어요.")
            return
        }
        val list = audioManager.availableCommunicationDevices
        for(dev in list){
            Log.d("devList", "${dev.productName}")
        }
        //audioManager.setCommunicationDevice()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deviceInfo = audioManager.communicationDevice
            Log.d(TAG, "설비 사용: ${deviceInfo?.id}, ${deviceInfo?.productName}")
        }

        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (outputDevice in outputDevices) {
            val type = when (outputDevice.type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "이어폰 스피커 (사용하는 이어폰을 대표하지 않음)"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "내장 스피커 시스템(즉, 모노 스피커 또는 스테레오 스피커)"
                AudioDeviceInfo.TYPE_TELEPHONY -> "전화 네트워크 전송 오디오"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "전화에 사용되는 블루투스 장치"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP 프로파일을 지원하는 블루투스 장치"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "TYPE_BLE_HEADSET"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "TYPE_WIRED_HEADPHONES"
                else -> "기타 ${outputDevice.type}"
            }
            Log.d(
                TAG,
                "설비${outputDevice.id} ${outputDevice.productName} $type isSink=${outputDevice.isSink} isSource=${outputDevice.isSource}"
            )
        }

    }

    fun increaseVolume() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND or AudioManager.FLAG_SHOW_UI)
    }
    fun decreaseVolume() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND or AudioManager.FLAG_SHOW_UI)
    }


    @SuppressLint("NewApi")
    fun hasSpeakerphone(): Boolean {
        return if (build.getVersion() >= Build.VERSION_CODES.M &&
            MyApplication.applicationContext().packageManager
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        ) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    Log.d(TAG, "Speakerphone available")
                    return true
                }
            }
            false
        } else {
            Log.d(TAG, "Speakerphone available")
            true
        }
    }

    @SuppressLint("NewApi")
    fun setAudioFocus() {
        // Request audio focus before making any device switch.
        if (build.getVersion() >= Build.VERSION_CODES.O) {
            audioRequest = audioFocusRequest.buildRequest(audioFocusChangeListener)
            audioRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
        /*
         * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
         * required to be in this mode when playout and/or recording starts for
         * best possible VoIP performance. Some devices have difficulties with speaker mode
         * if this is not set.
         */
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun enableBluetoothSco(enable: Boolean) {
        val audioManager = MyApplication.applicationContext()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (enable) {
            //audioManager.mode = AudioManager.MODE_IN_CALL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bluetoothScoDevice =
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }

                if (bluetoothScoDevice != null) {
                    audioManager.setCommunicationDevice(bluetoothScoDevice)
                } else {
                    Log.w(TAG, "Bluetooth SCO device not found")
                }
            } else {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "Started Bluetooth SCO using startBluetoothSco()")
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    @SuppressLint("NewApi")
    fun enableSpeakerphone(enable: Boolean) {
        audioManager.isSpeakerphoneOn = enable

        /**
         * Some Samsung devices (reported Galaxy s9, s21) fail to route audio through USB headset
         * when in MODE_IN_COMMUNICATION
         */
        if (!audioManager.isSpeakerphoneOn && "^SM-G(960|99)".toRegex().containsMatchIn(Build.MODEL)) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    break
                }
            }
        }
    }

    fun mute(mute: Boolean) {
        audioManager.isMicrophoneMute = mute
    }

    // TODO Consider persisting audio state in the event of process death
    fun cacheAudioState() {
        savedAudioMode = audioManager.mode
        savedIsMicrophoneMuted = audioManager.isMicrophoneMute
        savedSpeakerphoneEnabled = audioManager.isSpeakerphoneOn
    }

    @SuppressLint("NewApi")
    fun restoreAudioState() {
        audioManager.mode = savedAudioMode
        mute(savedIsMicrophoneMuted)
        enableSpeakerphone(savedSpeakerphoneEnabled)
        if (build.getVersion() >= Build.VERSION_CODES.O) {
            audioRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }
}