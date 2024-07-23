package ai.ableai.bhs.viewmodel

import ai.ableai.bhs.*
import ai.ableai.bhs.audio.AudioDeviceManager
import ai.ableai.bhs.bluetoothheadset.BluetoothHeadsetManager.HeadsetState
import ai.ableai.bhs.ble.BleManager
import ai.ableai.bhs.bluetoothheadset.BluetoothHeadsetConnectionListener
import ai.ableai.bhs.bluetoothheadset.BluetoothHeadsetManager
import ai.ableai.bhs.service.HeadsetService
import ai.ableai.bhs.sherpa.*
import ai.ableai.bhs.util.Utils.Companion.copyDataDir
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.AssetManager
import android.media.*
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.*
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule


const val TAG = "MainViewModel"

@SuppressLint("MissingPermission")
class MainViewModel : IMainViewModel() {

    private val bluetoothHeadsetManager = BluetoothHeadsetManager.get()
    private val audioManager = AudioDeviceManager.get()
    private val bleManager = BleManager.get()

    private val _eventFlow = MutableSharedFlow<Event>()
    override val eventFlow = _eventFlow.asSharedFlow()

    private val _headsetState = MutableStateFlow<HeadsetState>(HeadsetState.Disconnected)
    override val headsetState: StateFlow<HeadsetState> = _headsetState

    private val _inProgress = MutableStateFlow<Boolean>(false)
    override val inProgress: StateFlow<Boolean> = _inProgress

    private val _progressState = MutableStateFlow<String>("")
    override val progressState: StateFlow<String> = _progressState

    private val _hasPermission = MutableStateFlow<Boolean>(false)
    override val hasPermission: StateFlow<Boolean> = _hasPermission

    private val _pairedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    override val pairedDevices: StateFlow<Set<BluetoothDevice>> = _pairedDevices

    private val _scanedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    override val scanedDevices: StateFlow<Set<BluetoothDevice>> = _scanedDevices

    private val _micAmplitudeData = MutableStateFlow<List<Float>>(emptyList())
    override val micAmplitudeData: StateFlow<List<Float>> = _micAmplitudeData

    private val _recording = MutableStateFlow<Boolean>(false)
    override val recording: StateFlow<Boolean> = _recording

    private val _stateTxt = MutableStateFlow<String>("")
    override val stateTxt: StateFlow<String> = _stateTxt
    private val _asrTxt = MutableStateFlow<String>("")
    override val asrTxt: StateFlow<String> = _asrTxt

    private val audioSource = MediaRecorder.AudioSource.MIC

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val sampleRateInHz = 16000
    private var audioRecord: AudioRecord? = null

    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private lateinit var onlineRecognizer: OnlineRecognizer
    private lateinit var offlineRecognizer: OfflineRecognizer

    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack

    private var headsetService: HeadsetService? = null
    private var serviceBound = false


    init {
        observeBluetoothHeadsetManager()

    }



    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as HeadsetService.LocalBinder
            Log.d(TAG, "ServiceConnection: connected to service.")
            headsetService = binder.getService()
            serviceBound = true
            //start recording
            audioRecord?.let {
                headsetService?.startRecording(
                    it,
                    sampleRateInHz,
                    onlineRecognizer,
                    null,
                    tts,
                    track,
                    { samples ->
                        _micAmplitudeData.value = samples.toList()
                    },
                    { textToDisplay ->
                        _asrTxt.value = textToDisplay
                    }
                )
            }
            _recording.value = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "ServiceConnection: disconnected from service.")
            serviceBound = false
            stopRecording()
        }
    }



    fun bindService() {
        if(!serviceBound) {
            Intent(MyApplication.applicationContext(), HeadsetService::class.java).also { intent ->
                MyApplication.applicationContext()
                    .bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    fun unbindService() {
        if (serviceBound) {
            MyApplication.applicationContext().unbindService(connection)
            headsetService?.stopSelf()
            serviceBound = false
        }
    }

    override fun resumeHeadsetState(){
        if(headsetService?.recording==true){
            _recording.value = true
        }
//        Log.d("DEBUG", "bluetoothHeadsetManager.hasConnectedDevice() ${bluetoothHeadsetManager.hasConnectedDevice()}")
//        if (bluetoothHeadsetManager.hasConnectedDevice()) {
//            bluetoothHeadsetManager.connect()
//        }
    }

    private val connectionStateListener: (RxBleDevice, RxBleConnection.RxBleConnectionState) -> Unit = { device, connectionState ->
        when(connectionState){
            RxBleConnection.RxBleConnectionState.CONNECTED -> {
                Log.d("RxBle", "CONNECTED")
            }
            RxBleConnection.RxBleConnectionState.CONNECTING -> {
                Log.d("RxBle", "CONNECTING")
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                Log.d("RxBle", "DISCONNECTED")
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
                Log.d("RxBle", "DISCONNECTING")
            }
        }
    }

    private fun bleConnect(){
        var headSetDevice: BluetoothDevice? = null
        bluetoothHeadsetManager.connectedDevice()?.forEach { device->
            device.uuids.forEach { uuid->
                Log.d("headsetDevices", uuid.uuid.toString())
                if(uuid.uuid.toString()=="0000111d-d102-11e1-9b23-00025b00a5a5") {
                    headSetDevice = device
                    return@forEach
                }
            }
        }
        headSetDevice?.let { bleManager.connectDevice(it, connectionStateListener) }
    }
    private fun bleDisconnect() = bleManager.disconnectDevice()


    private fun observeBluetoothHeadsetManager() {
        viewModelScope.launch {
            bluetoothHeadsetManager.headsetStateValue.collect { state ->
                _headsetState.value = state
                when (_headsetState.value){
                    HeadsetState.Connected -> {
                        //bleConnect()
                    }
                    HeadsetState.AudioActivated -> {
                        //bleConnect()
                    }
                    HeadsetState.Disconnected-> {
                        //bleDisconnect()
                    }
                    else->{

                    }
                }
            }
        }
    }

    override fun updatePairedDevice() {
        _pairedDevices.value = bluetoothHeadsetManager.getPairedDevices()
    }



    override fun initTts() {
        var modelDir: String?
        var modelName: String?
        var ruleFsts: String?
        var ruleFars: String?
        var lexicon: String?
        var dataDir: String?
        var dictDir: String?
        var assets: AssetManager? = MyApplication.applicationContext().assets

        // The purpose of such a design is to make the CI test easier
        // Please see
        // https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py
        modelDir = "vits-mimic3-ko_KO-kss_low"
        modelName = "ko_KO-kss_low.onnx"
        ruleFsts = null
        ruleFars = null
        lexicon = null
        dataDir = "vits-mimic3-ko_KO-kss_low/espeak-ng-data"
        dictDir = null

        if (dataDir != null) {
            val newDir = copyDataDir(modelDir!!)
            modelDir = newDir + "/" + modelDir
            dataDir = newDir + "/" + dataDir
            assets = null
        }

        if (dictDir != null) {
            val newDir = copyDataDir(modelDir!!)
            modelDir = newDir + "/" + modelDir
            dictDir = modelDir + "/" + "dict"
            ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
            assets = null
        }

        val config = getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = modelName,
            lexicon = lexicon ?: "",
            dataDir = dataDir ?: "",
            dictDir = dictDir ?: "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: "",
        )

        tts = OfflineTts(assetManager = assets, config = config)

        Log.i(TAG, "Finish initializing TTS")
        Log.i(TAG, "Start to initialize AudioTrack")
        initAudioTrack()
        Log.i(TAG, "Finish initializing AudioTrack")
    }
    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }

    override fun initOnlineRecognizer() {
        // Please change getModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val firstType = 14
        val firstRuleFsts: String?
        firstRuleFsts = null
        Log.i(TAG, "Select model type $firstType for the first pass")
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getModelConfig(type = firstType)!!,
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )
        if (firstRuleFsts != null) {
            config.ruleFsts = firstRuleFsts
        }

        onlineRecognizer = OnlineRecognizer(
            assetManager = MyApplication.applicationContext().assets,
            config = config,
        )
    }
    override fun initOfflineRecognizer() {
        // Please change getOfflineModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val secondType = 13
        var secondRuleFsts: String?
        secondRuleFsts = null
        Log.i(TAG, "Select model type $secondType for the second pass")

        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getOfflineModelConfig(type = secondType)!!,
        )

        if (secondRuleFsts != null) {
            config.ruleFsts = secondRuleFsts
        }

        offlineRecognizer = OfflineRecognizer(
            assetManager = MyApplication.applicationContext().assets,
            config = config,
        )
    }

    private val bluetoothHeadsetConnectionListener = object: BluetoothHeadsetConnectionListener {
        override fun onBluetoothHeadsetStateChanged(headsetName: String?, state: Int) {
            Log.d("BluetoothHeadsetConnectionListener", "onBluetoothHeadsetStateChanged, headsetName:${headsetName}, state:${state}")
            when(state){
                BluetoothProfile.STATE_CONNECTED->{
                    setInProgress(false, "")
                    _scanedDevices.value = emptySet()
                    _pairedDevices.value = bluetoothHeadsetManager.getPairedDevices()
                    //Utils.showNotification("연결되었습니다.")
                }
                BluetoothProfile.STATE_DISCONNECTED->{
                    //_headsetState.value = HeadsetState.Disconnected
                }

            }
        }

        override fun onBluetoothScoStateChanged(state: Int) {
            Log.d("BluetoothHeadsetConnectionListener", "onBluetoothScoStateChanged, state:${state}")
        }

        override fun onBluetoothHeadsetActivationError() {
            Log.d("BluetoothHeadsetConnectionListener", "onBluetoothHeadsetActivationError")
        }

        override fun onBluetoothDeviceFound(device: BluetoothDevice) {
            val device_name = device.name
            val device_Address = device.address
            Log.d("device_name", device_name?:"null")
            if (device_name != null) {
                var newSet: Set<BluetoothDevice> = emptySet()
                for (device1 in _scanedDevices.value) {
                    newSet = newSet + device1
                }
                newSet = newSet + device
                Log.d("newSet","${newSet}")
                _scanedDevices.value = newSet
            }


        }

    }




    override fun start() {
        bluetoothHeadsetManager.setProfile()
        bluetoothHeadsetManager.start(bluetoothHeadsetConnectionListener)
    }

    override fun stop() {
        bluetoothHeadsetManager.stop()
        stopRecording()
        headsetService?.stopSelf()
    }

    override fun createBond(device: BluetoothDevice){
        bluetoothHeadsetManager.createBond(device)
    }

//    val connectError: LiveData<Event<Boolean>>
//        get() = bluetoothHeadsetRepository.connectError



    fun setInProgress(en: Boolean, msg: String){
        _progressState.value = msg
        _inProgress.value = en
    }

    override fun audioActivate() = bluetoothHeadsetManager.activate()
    private fun audioDeactivate() = bluetoothHeadsetManager.deactivate()



    override fun startScan(){
        _progressState.value = "Scanning...."
        _scanedDevices.value = emptySet()
        bluetoothHeadsetManager.scanDevice()
        Timer("SettingUp", false).schedule(7000) {
            bluetoothHeadsetManager.stopScanDeivce()
        }

    }
    override fun connectDevice(device: BluetoothDevice){
        setInProgress(true, "Connecting....")
        bluetoothHeadsetManager.connectDevice(device)
    }



    override fun onClickDisconnect() {
        bluetoothHeadsetManager.disconnectDevice()
    }


    private fun initMicrophone(): Boolean {

        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        Log.i(
            TAG, "buffer size in milliseconds: ${numBytes * 1000.0f / sampleRateInHz}"
        )

        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )
        return true
    }



    override fun recordAudio() {
        audioActivate()
        val ret = initMicrophone()
        if (!ret) {
            Log.e(TAG, "Failed to initialize microphone")
            event(Event.showNotification("Failed to initialize microphone"))
            return
        }
        bindService()

    }



    override fun stopRecording() {
        headsetService?.stopRecording(audioRecord)
        unbindService()
        _recording.value = false
        audioDeactivate()
    }


    private fun event(event: Event) {
        viewModelScope.launch {
            _eventFlow.emit(event)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothHeadsetManager.stop()
        headsetService?.stopSelf()
    }

}

/**
 * For Compose Preview
 */
abstract class IMainViewModel : ViewModel() {

    abstract val eventFlow: SharedFlow<Event>
    abstract val headsetState: StateFlow<HeadsetState>
    abstract val inProgress: StateFlow<Boolean>
    abstract val progressState: StateFlow<String>
    abstract val pairedDevices: StateFlow<Set<BluetoothDevice>>
    abstract val scanedDevices: StateFlow<Set<BluetoothDevice>>
    abstract val hasPermission: StateFlow<Boolean>
    abstract val recording: StateFlow<Boolean>
    abstract val micAmplitudeData: StateFlow<List<Float>>
    abstract val stateTxt: StateFlow<String>
    abstract val asrTxt: StateFlow<String>

    abstract fun initOnlineRecognizer()
    abstract fun initOfflineRecognizer()
    abstract fun initTts()
    abstract fun start()
    abstract fun stop()
    abstract fun startScan()
    abstract fun audioActivate()
    abstract fun onClickDisconnect()
    abstract fun connectDevice(device: BluetoothDevice)
    abstract fun updatePairedDevice()
    abstract fun createBond(device: BluetoothDevice)
    abstract fun recordAudio()
    abstract fun stopRecording()
    abstract fun resumeHeadsetState()

    sealed class Event {
        data class showNotification(val msg: String) : Event()
    }

}
class MainViewModelPreview : IMainViewModel() {
    override val eventFlow: SharedFlow<Event> = MutableSharedFlow<Event>()
    override val headsetState: StateFlow<HeadsetState> = MutableStateFlow(HeadsetState.AudioActivated)
    override val inProgress: StateFlow<Boolean> = MutableStateFlow(false)
    override val progressState: StateFlow<String> = MutableStateFlow("")
    override val pairedDevices: StateFlow<Set<BluetoothDevice>> =  MutableStateFlow(emptySet())
    override val hasPermission: StateFlow<Boolean> = MutableStateFlow(false)
    override val scanedDevices: StateFlow<Set<BluetoothDevice>> = MutableStateFlow(emptySet())
    override val recording: StateFlow<Boolean> = MutableStateFlow(false)
    override val micAmplitudeData: StateFlow<List<Float>> = MutableStateFlow(emptyList())
    override val stateTxt: StateFlow<String> = MutableStateFlow("")
    override val asrTxt: StateFlow<String> = MutableStateFlow("")

    override fun initOnlineRecognizer() {}
    override fun initOfflineRecognizer() {}
    override fun initTts() {}
    override fun start() {}
    override fun stop() {}
    override fun startScan() {}
    override fun audioActivate() {}
    override fun onClickDisconnect() {}
    override fun connectDevice(device: BluetoothDevice) {}
    override fun updatePairedDevice() {}
    override fun createBond(device: BluetoothDevice) {}
    override fun recordAudio() {}
    override fun stopRecording() {}
    override fun resumeHeadsetState(){}
}