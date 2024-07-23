package com.lilly.bluetoothheadset.bluetoothheadset

import com.lilly.bluetoothheadset.audio.AudioDevice
import com.lilly.bluetoothheadset.audio.AudioDeviceManager
import com.lilly.bluetoothheadset.MyApplication
import com.lilly.bluetoothheadset.util.SystemClockWrapper
import com.lilly.bluetoothheadset.util.DefaultPermissionsCheckStrategy
import com.lilly.bluetoothheadset.util.PermissionsCheckStrategy
import com.lilly.bluetoothheadset.util.Utils
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothDevice.ACTION_FOUND
import android.bluetooth.BluetoothHeadset.*
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.*


private const val TAG = "BluetoothHeadsetManager"
private const val PERMISSION_ERROR_MESSAGE = "Bluetooth unsupported, permissions not granted"

@SuppressLint("MissingPermission")
class BluetoothHeadsetManager(
    private val audioDeviceManager: AudioDeviceManager
): BluetoothProfile.ServiceListener, BroadcastReceiver() {

    var headsetListener: BluetoothHeadsetConnectionListener? = null
    val bluetoothScoHandler: Handler = Handler(Looper.getMainLooper())
    val systemClockWrapper: SystemClockWrapper = SystemClockWrapper()
    private val bluetoothIntentProcessor: BluetoothIntentProcessor = BluetoothIntentProcessorImpl()
    private val bleManager: BluetoothManager = MyApplication.applicationContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter = bleManager.adapter
    var mLeAudioCallbackRegistered: Boolean = false


    private val permissionsRequestStrategy: PermissionsCheckStrategy = DefaultPermissionsCheckStrategy()
    private var hasRegisteredReceivers: Boolean = false
    companion object {
        private var INSTANCE: BluetoothHeadsetManager? = null

        fun initialize(){
            if (INSTANCE == null){
                INSTANCE = BluetoothHeadsetManager(AudioDeviceManager.get())
            }
        }

        fun get(): BluetoothHeadsetManager {
            return INSTANCE ?:
            throw IllegalStateException("BluetoothHeadsetManager must be initialized")
        }
    }

    private var headsetProxy: BluetoothHeadset? = null
    var A2DPProxy: BluetoothA2dp? = null

    private val _headsetStateValue = MutableStateFlow<HeadsetState>(HeadsetState.Disconnected)
    val headsetStateValue: StateFlow<HeadsetState> = _headsetStateValue


    internal val enableBluetoothScoJob: EnableBluetoothScoJob = EnableBluetoothScoJob(
        audioDeviceManager,
        bluetoothScoHandler,
        systemClockWrapper,
    )

    internal val disableBluetoothScoJob: DisableBluetoothScoJob = DisableBluetoothScoJob(
        audioDeviceManager,
        bluetoothScoHandler,
        systemClockWrapper,
    )

    override fun onServiceConnected(profile: Int, bluetoothProfile: BluetoothProfile) {
        if (profile == BluetoothProfile.HEADSET) {
            headsetProxy = bluetoothProfile as BluetoothHeadset
            Log.d(TAG, "onServiceConnected: headsetProxy")
            bluetoothProfile.connectedDevices.forEach { device ->
                Log.d(TAG, "Bluetooth " + device.name + " connected")
            }
            if (hasConnectedDevice()) {
                connect()
                headsetListener?.onBluetoothHeadsetStateChanged(getHeadsetName())
            }
        }else if(profile == BluetoothProfile.A2DP){
            A2DPProxy = bluetoothProfile as BluetoothA2dp
            Log.d(TAG, "onServiceConnected: A2DPProxy")
        }

    }

    override fun onServiceDisconnected(profile: Int) {
        Log.d(TAG, "Bluetooth disconnected")
        if (profile == BluetoothProfile.HEADSET) {
            _headsetStateValue.value = HeadsetState.Disconnected
            headsetListener?.onBluetoothHeadsetStateChanged()
            headsetProxy = null
        }else if(profile == BluetoothProfile.A2DP){
            A2DPProxy = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun test(){
        Log.d("TAG", "${bluetoothAdapter.isLeAudioSupported}, ${bluetoothAdapter.isLeAudioBroadcastAssistantSupported}")
    }

    fun scanDevice(){
        _headsetStateValue.value = HeadsetState.Scanning
        bluetoothAdapter.startDiscovery() //블루투스 기기 검색 시작
    }
    fun stopScanDeivce(){
        bluetoothAdapter.cancelDiscovery()
        _headsetStateValue.value = when {
            hasActiveHeadset() -> {
                HeadsetState.AudioActivated
            }
            hasConnectedDevice() -> {
                HeadsetState.Connected
            }
            else -> {
                HeadsetState.Disconnected
            }
        }
    }
    fun getPairedDevices() = bluetoothAdapter.bondedDevices ?: emptySet()

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("DiscouragedPrivateApi", "BlockedPrivateApi")
    fun connectDevice(device: BluetoothDevice){
        try {
            val connect: Method =
                BluetoothHeadset::class.java.getDeclaredMethod(
                    "connect",
                    BluetoothDevice::class.java
                )
            val a2dpconnect: Method = BluetoothA2dp::class.java.getDeclaredMethod(
                "connect",
                BluetoothDevice::class.java
            )
            connect.isAccessible = true
            connect.invoke(headsetProxy, device)
            a2dpconnect.isAccessible = true
            a2dpconnect.invoke(A2DPProxy, device)




            Log.d("BluetoothProfile", "BluetoothProfile.HEADSET Connected")

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                Utils.showNotification(e.message.toString())
            }
            disconnect()
        } finally {
            connect()
            //audioDeviceManager.enableBluetoothSco(true)
        }
    }
    @SuppressLint("DiscouragedPrivateApi")
    fun disconnectDevice(){
        val headsetConnectedDevices = headsetProxy?.connectedDevices
        val targetDevice = headsetConnectedDevices //headsetConnectedDevices?.filter { it.name?.startsWith("NXH") == true }
        if(targetDevice==null || targetDevice.isEmpty()){
            _headsetStateValue.value = HeadsetState.Disconnected
            return
        }
        try {
            val disconnect: Method = BluetoothHeadset::class.java.getDeclaredMethod(
                "disconnect",
                BluetoothDevice::class.java
            )
            val a2dpdisconnect: Method = BluetoothA2dp::class.java.getDeclaredMethod(
                "disconnect",
                BluetoothDevice::class.java
            )
            disconnect.isAccessible = true
            disconnect.invoke(headsetProxy, targetDevice[0])
            a2dpdisconnect.isAccessible = true
            a2dpdisconnect.invoke(A2DPProxy, targetDevice[0])
        }catch (e:Exception ) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                Utils.showNotification(e.message.toString())
            }
        }finally {
            disconnect()
        }
    }
    fun createBond(device:BluetoothDevice) = device.createBond()


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("intent_action", intent.action.toString())
        if (isCorrectIntentAction(intent.action)) {
            intent.getHeadsetDevice()?.let { bluetoothDevice ->
                when (intent.action) {
                    ACTION_FOUND -> {
                        headsetListener?.onBluetoothDeviceFound(bluetoothDevice.device)
                    }
                    ACTION_BOND_STATE_CHANGED->{
                        Log.d("action", "bond_state_changed")
                        connectDevice(bluetoothDevice.device)
                    }
                    else -> {}
                }
            }


            intent.getHeadsetDevice()?.let { bluetoothDevice ->
                intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, STATE_DISCONNECTED).let { state ->
                    when (state) {
                        STATE_CONNECTED -> {
                            Log.d(
                                TAG,
                                "Bluetooth headset $bluetoothDevice connected",
                            )
                            connect()
                            //activate()
                            headsetListener?.onBluetoothHeadsetStateChanged(bluetoothDevice.name, STATE_CONNECTED)
                        }
                        STATE_DISCONNECTED -> {
                            Log.d(
                                TAG,
                                "Bluetooth headset $bluetoothDevice disconnected",
                            )
                            disconnect()
                            headsetListener?.onBluetoothHeadsetStateChanged(bluetoothDevice.name, STATE_DISCONNECTED)
                        }
                        STATE_AUDIO_CONNECTED -> {
                            Log.d(TAG, "Bluetooth audio connected on device $bluetoothDevice")
                            enableBluetoothScoJob.cancelBluetoothScoJob()
                            _headsetStateValue.value = HeadsetState.AudioActivated
                            headsetListener?.onBluetoothHeadsetStateChanged(bluetoothDevice.name, STATE_AUDIO_CONNECTED)
                        }
                        STATE_AUDIO_DISCONNECTED -> {
                            Log.d(TAG, "Bluetooth audio disconnected on device $bluetoothDevice")
                            disableBluetoothScoJob.cancelBluetoothScoJob()
                            /*
                             * This block is needed to restart bluetooth SCO in the event that
                             * the active bluetooth headset has changed.
                             */
//                            if (hasActiveHeadsetChanged()) {
//                                enableBluetoothScoJob.executeBluetoothScoJob()
//                            }else{
//                                disconnect()
//                            }
                            disconnect()

                            headsetListener?.onBluetoothHeadsetStateChanged(bluetoothDevice.name, STATE_AUDIO_DISCONNECTED)
                        }
                        else -> {}
                    }
                }
            }
            intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, SCO_AUDIO_STATE_DISCONNECTED).let { state ->
                when (state) {
                    SCO_AUDIO_STATE_CONNECTING -> {
                        Log.d(
                            TAG,
                            "Bluetooth SCO connecting",
                        )

                        headsetListener?.onBluetoothScoStateChanged(
                            SCO_AUDIO_STATE_CONNECTING,
                        )
                    }
                    SCO_AUDIO_STATE_CONNECTED -> {
                        Log.d(
                            TAG,
                            "Bluetooth SCO connected",
                        )

                        headsetListener?.onBluetoothScoStateChanged(
                            SCO_AUDIO_STATE_CONNECTED,
                        )
                    }
                    SCO_AUDIO_STATE_DISCONNECTED -> {
                        Log.d(
                            TAG,
                            "Bluetooth SCO disconnected",
                        )

                        headsetListener?.onBluetoothScoStateChanged(
                            SCO_AUDIO_STATE_DISCONNECTED,
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private val bluetoothHeadsetFilter = IntentFilter().apply {
        addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        addAction(ACTION_FOUND)
    }

    //@RequiresApi(Build.VERSION_CODES.TIRAMISU)


    @RequiresApi(Build.VERSION_CODES.Q)
    fun setProfile() {

        MyApplication.applicationContext().registerReceiver(this, bluetoothHeadsetFilter)

        if (hasPermissions()) {

            bluetoothAdapter.getProfileProxy(
                MyApplication.applicationContext(),
                this,
                BluetoothProfile.HEADSET,
            )
            bluetoothAdapter.getProfileProxy(
                MyApplication.applicationContext(),
                this,
                BluetoothProfile.A2DP,
            )


        } else {
            Log.w(TAG, PERMISSION_ERROR_MESSAGE)
        }


    }

    fun start(headsetListener: BluetoothHeadsetConnectionListener) {

        MyApplication.applicationContext().registerReceiver(this, bluetoothHeadsetFilter)

        this.headsetListener = headsetListener

        bluetoothAdapter.getProfileProxy(
            MyApplication.applicationContext(),
            this,
            BluetoothProfile.HEADSET,
        )
        bluetoothAdapter.getProfileProxy(
            MyApplication.applicationContext(),
            this,
            BluetoothProfile.A2DP,
        )
        if (!hasRegisteredReceivers) {
            MyApplication.applicationContext().registerReceiver(
                this,
                IntentFilter(ACTION_CONNECTION_STATE_CHANGED),
            )
            MyApplication.applicationContext().registerReceiver(
                this,
                IntentFilter(ACTION_AUDIO_STATE_CHANGED),
            )
            MyApplication.applicationContext().registerReceiver(
                this,
                IntentFilter(ACTION_SCO_AUDIO_STATE_UPDATED),
            )
            MyApplication.applicationContext().registerReceiver(
                this,
                IntentFilter(ACTION_BOND_STATE_CHANGED),
            )
            hasRegisteredReceivers = true
        }

    }

    fun stop() {
        headsetListener = null
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProxy)
        if (hasRegisteredReceivers) {
            MyApplication.applicationContext().unregisterReceiver(this)
            hasRegisteredReceivers = false
        }
    }

    fun activate() {
        if (_headsetStateValue.value == HeadsetState.Connected || _headsetStateValue.value == HeadsetState.AudioActivationError) {

            enableBluetoothScoJob.executeBluetoothScoJob()
        } else {
            Log.w(TAG, "Cannot activate when in the ${_headsetStateValue.value::class.simpleName} state")
        }
    }

    fun deactivate() {
        if (_headsetStateValue.value == HeadsetState.AudioActivated) {
            disableBluetoothScoJob.executeBluetoothScoJob()
        } else {
            Log.w(TAG, "Cannot deactivate when in the ${_headsetStateValue.value::class.simpleName} state")
        }
    }

    fun hasActivationError(): Boolean {
        return if (hasPermissions()) {
            _headsetStateValue.value == HeadsetState.AudioActivationError
        } else {
            Log.w(TAG, PERMISSION_ERROR_MESSAGE)
            false
        }
    }

    // TODO Remove bluetoothHeadsetName param
    fun getHeadset(bluetoothHeadsetName: String?): AudioDevice.BluetoothHeadset? {
        return if (hasPermissions()) {
            if (_headsetStateValue.value != HeadsetState.Disconnected) {
                val headsetName = bluetoothHeadsetName ?: getHeadsetName()
                headsetName?.let { AudioDevice.BluetoothHeadset(it) }
                    ?: AudioDevice.BluetoothHeadset()
            } else {
                null
            }
        } else {
            Log.w(TAG, PERMISSION_ERROR_MESSAGE)
            null
        }
    }

    private fun isCorrectIntentAction(intentAction: String?) =
        intentAction == ACTION_CONNECTION_STATE_CHANGED || intentAction == ACTION_AUDIO_STATE_CHANGED || intentAction == ACTION_SCO_AUDIO_STATE_UPDATED || intentAction == ACTION_FOUND

    fun connect() {
        if (!hasActiveHeadset()) {
            _headsetStateValue.value = HeadsetState.Connected
            //activate()
        }else{
            _headsetStateValue.value = HeadsetState.AudioActivated
        }
    }

    private fun disconnect() {
        _headsetStateValue.value = when {
            hasActiveHeadset() -> {
                HeadsetState.AudioActivated
            }
            hasConnectedDevice() -> {
                HeadsetState.Connected
            }
            else -> {
                HeadsetState.Disconnected
            }
        }
    }

    private fun hasActiveHeadsetChanged() = _headsetStateValue.value == HeadsetState.AudioActivated && hasConnectedDevice() && !hasActiveHeadset()

    private fun getHeadsetName(): String? =
        headsetProxy?.let { proxy ->
            proxy.connectedDevices?.let { devices ->
                when {
                    devices.size > 1 && hasActiveHeadset() -> {
                        val device = devices.find { proxy.isAudioConnected(it) }?.name
                        Log.d(TAG, "Device size > 1 with device name: $device")
                        device
                    }
                    devices.size == 1 -> {
                        val device = devices.first().name
                        Log.d(TAG, "Device size 1 with device name: $device")
                        device
                    }
                    else -> {
                        Log.d(TAG, "Device size 0")
                        null
                    }
                }
            }
        }


    private fun hasActiveHeadset() =
        headsetProxy?.let { proxy ->
            proxy.connectedDevices?.let { devices ->
                devices.any { proxy.isAudioConnected(it) }
            }
        } ?: false

    fun hasConnectedDevice(): Boolean {
        Log.d("DEBUG", "headsetProxy ${headsetProxy}")
        return headsetProxy?.let { proxy ->
            proxy.connectedDevices?.let { devices ->
                devices.isNotEmpty()
            }
        } ?: false
    }
    fun connectedDevice() =  headsetProxy?.let { proxy ->
        proxy.connectedDevices
    }

    private fun Intent.getHeadsetDevice(): BluetoothDeviceWrapper? =
        bluetoothIntentProcessor.getBluetoothDevice(this)?.let { device ->
            if (isHeadsetDevice(device)) device else null
        }

    private fun isHeadsetDevice(deviceWrapper: BluetoothDeviceWrapper): Boolean =
        deviceWrapper.deviceClass?.let { deviceClass ->
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
        } ?: false

    internal fun hasPermissions() = permissionsRequestStrategy.hasPermissions()

    sealed class HeadsetState {
        object Disconnected : HeadsetState()
        object Scanning : HeadsetState()
        object Connected : HeadsetState()
        object AudioActivating : HeadsetState()
        object AudioActivationError : HeadsetState()
        object AudioActivated : HeadsetState()
    }

    internal inner class EnableBluetoothScoJob(
        private val audioDeviceManager: AudioDeviceManager,
        bluetoothScoHandler: Handler,
        systemClockWrapper: SystemClockWrapper,
    ) : BluetoothScoJob(bluetoothScoHandler, systemClockWrapper) {

        override fun scoAction() {
            Log.d(TAG, "Attempting to enable bluetooth SCO")
            audioDeviceManager.enableBluetoothSco(true)
            _headsetStateValue.value = HeadsetState.AudioActivating
        }

        override fun scoTimeOutAction() {
            _headsetStateValue.value = HeadsetState.AudioActivationError
            headsetListener?.onBluetoothHeadsetActivationError()
        }
    }

    internal inner class DisableBluetoothScoJob(
        private val audioDeviceManager: AudioDeviceManager,
        bluetoothScoHandler: Handler,
        systemClockWrapper: SystemClockWrapper,
    ) : BluetoothScoJob(bluetoothScoHandler, systemClockWrapper) {

        override fun scoAction() {
            Log.d(TAG, "Attempting to disable bluetooth SCO")
            audioDeviceManager.enableBluetoothSco(false)
            _headsetStateValue.value = HeadsetState.Connected
        }

        override fun scoTimeOutAction() {
            _headsetStateValue.value = HeadsetState.AudioActivationError
        }
    }


}