package ai.ableai.bhs.service
import ai.ableai.bhs.MyApplication
import ai.ableai.bhs.R
import ai.ableai.bhs.audio.AudioDeviceManager
import ai.ableai.bhs.ble.BleManager
import ai.ableai.bhs.sherpa.OfflineRecognizer
import ai.ableai.bhs.sherpa.OfflineTts
import ai.ableai.bhs.sherpa.OnlineRecognizer
import ai.ableai.bhs.ui.MainActivity
import ai.ableai.bhs.util.CHARA_STRING
import ai.ableai.bhs.util.Utils
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.*
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class HeadsetService : LifecycleService() {

    private val TAG = "HeadsetService"

    private var bleManager: BleManager = BleManager.get()
    private var audioDeviceManager: AudioDeviceManager = AudioDeviceManager.get()

    private var samplesBuffer = arrayListOf<FloatArray>()
    private var idx: Int = 0
    private var lastText: String = ""

    var recording = false

    private val localBinder: IBinder = LocalBinder()
    private var isServiceRunning = false

    private var mWriteDisposable: Disposable? = null


    private var generate: Boolean = true
    private var play: Boolean = false
    private var stopped: Boolean = false

    private lateinit var track: AudioTrack

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        //startForeground(1, createChannel().build())
        //isServiceRunning = true
        return START_STICKY
    }

    internal inner class LocalBinder : Binder() {
        fun getService(): HeadsetService = this@HeadsetService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        isServiceRunning = true
        handleBind()
        return localBinder
    }

    private fun handleBind() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26 이상은 포그라운드 서비스 시작
            startForegroundService(Intent(this@HeadsetService, HeadsetService::class.java))
        } else {
            // API 26 미만은 일반 서비스로 시작
            startService(Intent(this, HeadsetService::class.java))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(1, createChannel().build())
        } else {
            startForeground(1, createChannel().build(),
                FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or FOREGROUND_SERVICE_TYPE_MICROPHONE or FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }


    // 알림 채널을 생성하는 함수
    // API 26 이상에서는 알림 채널을 생성해야 합니다.
    // 알림 채널은 ID가 동일한 경우, 굳이 서비스를 재시작할 때 알림을 삭제하고 다시 생성하지 않아도 됩니다.
    // 동일한 ID의 알림은 업데이트 되는 방식이기 때문입니다.
    private fun createChannel(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val builder = if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            val channelId = "foreground_service_channel"
            val channel = NotificationChannel(
                channelId,
                "My Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
            NotificationCompat.Builder(this, channelId)
        } else {
            NotificationCompat.Builder(this)
        }

        builder.setSmallIcon(R.drawable.ic_headset_mic)
            .setContentTitle("Bluetooth Headset + STT")
            .setContentText("BT is Running")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
        }
        return builder
    }



    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1
        } else {
            track.stop()
            return 0
        }
    }

    private fun generateAudio(tts: OfflineTts, audioText: String) {
        val sidText = "1"
        val sidInt = sidText.toIntOrNull()
        if (sidInt == null || sidInt < 0) {
            Log.e(TAG, "Please input a non-negative integer for speaker ID!")
            return
        }
        val speed = "1.0"
        val speedFloat = speed.toFloatOrNull()
        if (speedFloat == null || speedFloat <= 0) {
            Log.e(TAG, "Please input a positive number for speech speed!")
            return
        }

        val textStr = audioText.trim()
        if (textStr.isBlank() || textStr.isEmpty()) {
            Log.e(TAG, "Please input a non-empty text!")
            return
        }

        track.pause()
        track.flush()
        track.play()

        play = false
        generate = false
        stopped = false

        Thread {
            val audio = tts.generateWithCallback(
                text = textStr,
                sid = sidInt,
                speed = speedFloat,
                callback = this::callback
            )

            val filename = MyApplication.applicationContext().filesDir.absolutePath + "/generated.wav"
            val ok = audio.samples.size > 0 && audio.save(filename)
            if (ok) {
                play = true
                generate = true
                track.stop()
            }
        }.start()
    }

    private fun volume_down(count: Int) =
        repeat(count) {
            mWriteDisposable = bleManager.writeData(CHARA_STRING, Utils.stringToByteArray("VOLUME 10 DOWN"))
                ?.subscribe({ writeBytes ->
                    Log.d("writtenBytes", "VOLUME 10 DOWN")
                }, { throwable ->
                    throwable.printStackTrace()
                })
        }

    private fun volume_up(count: Int) =
        repeat(count) {
            mWriteDisposable = bleManager.writeData(CHARA_STRING, Utils.stringToByteArray("VOLUME 10 UP"))
                ?.subscribe({ writeBytes ->
                    Log.d("writtenBytes", "VOLUME 10 UP")
                }, { throwable ->
                    throwable.printStackTrace()
                })
        }


    private fun commandProcess(tts: OfflineTts, text: String){
        Log.d("command", text)
        if(text.contains("소리") || text.contains("사운드") || text.contains("싸운드") || text.contains("볼륨")){
            if(text.contains("내려") || text.contains("줄여")|| text.contains("쭐여") || text.contains("낮춰") ||  text.contains("낮게")){
                generateAudio(tts, "소리를 줄입니다.")
                //audioDeviceManager.decreaseVolume()
                volume_down(3)
            }
            else if(text.contains("올려") || text.contains("울려") ||text.contains("키워") || text.contains("높여")|| text.contains("크게")){
                generateAudio(tts, "소리를 키웁니다.")
                //audioDeviceManager.increaseVolume()
                volume_up(3)
            }
        }
    }

    fun startRecording(
        audioRecord: AudioRecord,
        sampleRateInHz: Int,
        onlineRecognizer: OnlineRecognizer,
        offlineRecognizer: OfflineRecognizer?,
        tts: OfflineTts,
        _track: AudioTrack,
        amplitudeListener: (FloatArray) -> Unit,
        recordingListener: (String) -> Unit
    ){
        track = _track
        audioRecord.startRecording()
        recording = true
        samplesBuffer.clear()

        idx = 0


        CoroutineScope(Dispatchers.IO).launch {

            val stream = onlineRecognizer.createStream()

            val interval = 0.1 // i.e., 100 ms
            val bufferSize = (interval * sampleRateInHz).toInt() // in samples
            val buffer = ShortArray(bufferSize)

            while (recording) {
                val readSize = audioRecord.read(buffer, 0, buffer.size) ?: 0
                if (readSize != null && readSize > 0) {
                    val samples = FloatArray(readSize) { buffer[it] / 32768.0f }
                    samplesBuffer.add(samples)
                    amplitudeListener(samples)

                    stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                    while (onlineRecognizer.isReady(stream)) {
                        onlineRecognizer.decode(stream)
                    }
                    val isEndpoint = onlineRecognizer.isEndpoint(stream)
                    var textToDisplay = lastText

                    var text = onlineRecognizer.getResult(stream).text


                    // For streaming parformer, we need to manually add some
                    // paddings so that it has enough right context to
                    // recognize the last word of this segment
                    if (isEndpoint && onlineRecognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
                        val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
                        stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
                        while (onlineRecognizer.isReady(stream)) {
                            onlineRecognizer.decode(stream)
                        }
                        text = onlineRecognizer.getResult(stream).text
                    }



                    if (isEndpoint) {

                        onlineRecognizer.reset(stream)

                        if (text.isNotBlank()) {
                            //text = runSecondPass(offlineRecognizer, sampleRateInHz)
                            lastText = "${lastText}\n${idx}: $text"
                            idx += 1
                            commandProcess(tts, text)

                        } else {

                            samplesBuffer.clear()
                        }
                    }
                    if (text.isNotBlank()) {
                        textToDisplay = if (lastText.isBlank()) {
                            // textView.text = "${idx}: ${text}"
                            "${idx}: $text"
                        } else {
                            "${lastText}\n${idx}: $text"
                        }
                    }
                    recordingListener(textToDisplay)


                }

            }
            stream.release()
        }
    }

    private fun runSecondPass(offlineRecognizer: OfflineRecognizer, sampleRateInHz: Int): String {
        var totalSamples = 0
        for (a in samplesBuffer) {
            totalSamples += a.size
        }
        var i = 0

        val samples = FloatArray(totalSamples)

        // todo(fangjun): Make it more efficient
        for (a in samplesBuffer) {
            for (s in a) {
                samples[i] = s
                i += 1
            }
        }


        val n = maxOf(0, samples.size - 8000)

        samplesBuffer.clear()
        samplesBuffer.add(samples.sliceArray(n until samples.size))

        val stream = offlineRecognizer.createStream()
        stream.acceptWaveform(samples.sliceArray(0..n), sampleRateInHz)
        offlineRecognizer.decode(stream)
        val result = offlineRecognizer.getResult(stream)

        stream.release()

        return result.text
    }

    fun stopRecording(audioRecord: AudioRecord?){
        recording = false
        audioRecord?.stop()
        audioRecord?.release()
    }

    // 서비스가 언바인드 될 때 호출되는 함수
    override fun onUnbind(intent: Intent?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            stopSelf()
        }
        stopForeground(true)
        isServiceRunning = false
        return true
    }


    override fun onDestroy() {
        super.onDestroy()
        if (isServiceRunning) {
            stopService(Intent(this, HeadsetService::class.java))
            stopForeground(true)
            isServiceRunning = false
        }
        mWriteDisposable?.dispose()
    }
}