package ai.ableai.bhs.ble

import ai.ableai.bhs.MyApplication
import android.bluetooth.BluetoothDevice
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.disposables.Disposable
import java.util.*

class BleManager {

    companion object {
        private var INSTANCE: BleManager? = null

        fun initialize(){
            if (BleManager.Companion.INSTANCE == null){
                BleManager.Companion.INSTANCE = BleManager()
            }
        }

        fun get(): BleManager {
            return BleManager.Companion.INSTANCE ?:
            throw IllegalStateException("BleManager must be initialized")
        }
    }

    var rxBleConnection: RxBleConnection? = null
    private var mConnectSubscription: Disposable? = null

    var rxBleClient: RxBleClient = RxBleClient.create(MyApplication.applicationContext())

    private lateinit var connectionStateDisposable: Disposable

    /**
     * Connect & Discover Services
     * @Saved rxBleConnection
     */
    fun connectDevice(
        device: BluetoothDevice,
        connectionStateListener: (RxBleDevice, RxBleConnection.RxBleConnectionState) -> Unit
    ){
        val macAddress: String = device.address
        val rxDevice: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        connectDevice(rxDevice)

        connectionStateDisposable = rxDevice.observeConnectionStateChanges()
            .subscribe(
                { connectionState ->
                    connectionStateListener(rxDevice, connectionState)
                }
            ) { throwable ->
                throwable.printStackTrace()
            }
    }
    fun connectDevice(device: RxBleDevice){
        mConnectSubscription = device.establishConnection(false) // <-- autoConnect flag
            .flatMapSingle{ _rxBleConnection->
                // All GATT operations are done through the rxBleConnection.
                rxBleConnection = _rxBleConnection
                // Discover services
                _rxBleConnection.discoverServices()
            }.subscribe({
                // Services
            },{

            })
    }
    fun disconnectDevice(){
        mConnectSubscription?.dispose()
    }



    /**
     * Notification
     */
    fun bleNotification(uuid: String) = rxBleConnection
        ?.setupNotification(UUID.fromString(uuid))
        ?.doOnNext { notificationObservable->
            // Notification has been set up
        }
        ?.flatMap { notificationObservable -> notificationObservable }

    /**
     * Read
     */
    fun bleRead(uuid: String) =
        rxBleConnection?.readCharacteristic(UUID.fromString(uuid))


    /**
     * Write Data
     */
    fun writeData(uuid: String, sendByteData: ByteArray) = rxBleConnection?.writeCharacteristic(
        UUID.fromString(uuid),
        sendByteData
    )


}