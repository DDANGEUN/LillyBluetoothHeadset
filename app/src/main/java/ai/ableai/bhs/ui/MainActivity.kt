package ai.ableai.bhs.ui


import ai.ableai.bhs.*
import ai.ableai.bhs.util.Utils
import ai.ableai.bhs.util.Utils.Companion.repeatOnStarted
import ai.ableai.bhs.viewmodel.IMainViewModel
import ai.ableai.bhs.viewmodel.MainViewModel
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import ai.ableai.bhs.ui.theme.*
import ai.ableai.bhs.util.DefaultPermissionsCheckStrategy
import android.Manifest.permission.POST_NOTIFICATIONS
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat


private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()


    private val requestEnableBleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) { // TODO
                //Toast.makeText(this, "Bluetooth기능을 허용하였습니다.", Toast.LENGTH_SHORT).show()
                // 여기에서 Bluetooth가 활성화되었을 때 처리할 작업을 추가할 수 있습니다.
            } else {
                //Toast.makeText(this, "Bluetooth기능을 켜주세요.", Toast.LENGTH_SHORT).show()
                // Bluetooth가 활성화되지 않았을 때 처리할 작업을 추가할 수 있습니다.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionChecker = DefaultPermissionsCheckStrategy()
        setContent {
            LillyTheme {
                MainView(viewModel=viewModel, permissionChecker = permissionChecker)
            }
        }

        if (!permissionChecker.isBluetoothSupport()) {  //블루투스 지원 불가
            Utils.showNotification("Bluetooth is not supported.") // TODO
        }
        if (!permissionChecker.isBluetoothEnabled()){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBleLauncher.launch(enableBtIntent)
        }
        initObserver()
        if (permissionChecker.hasBluetoothConnectPermission()) {
            viewModel.start()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        viewModel.start()
                    } else {
                        // Permission denied, handle accordingly
                    }
                }.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(MyApplication.applicationContext(), POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, POST_NOTIFICATIONS)) {
                    // 이미 권한을 거절한 경우 권한 설정 화면으로 이동
                    val intent: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                        Uri.parse("package:" + this.packageName))
                    startActivity(intent)
                    this.finish()
                } else {
                    // 처음 권한 요청을 할 경우
                    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                        when (it) {
                            true -> {
                            }
                            false -> {
                                this.moveTaskToBack(true)
                                this.finishAndRemoveTask()
                            }
                        }
                    }.launch(POST_NOTIFICATIONS)
                }
            }
        }


        Log.i(TAG, "Start to initialize first-pass recognizer")
        viewModel.initOnlineRecognizer()
        Log.i(TAG, "Finished initializing first-pass recognizer")

        //Log.i(TAG, "Start to initialize second-pass recognizer")
        //viewModel.initOfflineRecognizer()
        //Log.i(TAG, "Finished initializing second-pass recognizer")

        Log.i(TAG, "Start to initialize TTS")
        viewModel.initTts()

    }






    private fun initObserver() {
        repeatOnStarted {
            viewModel.eventFlow.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: IMainViewModel.Event) = when (event) {
        is IMainViewModel.Event.showNotification -> {
            Utils.showNotification(event.msg)
        }
        else -> {

        }
    }


    override fun onResume() {
        super.onResume()
        viewModel.resumeHeadsetState()
    }

    override fun onDestroy() {
        viewModel.stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        viewModel.setInProgress(false, "")
    }


}



