package com.lilly.bluetoothheadset.ui

import com.lilly.bluetoothheadset.bluetoothheadset.BluetoothHeadsetManager
import com.lilly.bluetoothheadset.R
import com.lilly.bluetoothheadset.ui.theme.*
import com.lilly.bluetoothheadset.util.PermissionsCheckStrategy
import com.lilly.bluetoothheadset.util.PreviewPermissionChecker
import com.lilly.bluetoothheadset.util.Utils
import com.lilly.bluetoothheadset.viewmodel.IMainViewModel
import com.lilly.bluetoothheadset.viewmodel.MainViewModelPreview
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat

@Composable
fun MainView(
    viewModel: IMainViewModel,
    permissionChecker: PermissionsCheckStrategy
) {
    val context: Context = LocalContext.current
    val headsetState by viewModel.headsetState.collectAsState()
    val progressState by viewModel.progressState.collectAsState()
    val inProgress by viewModel.inProgress.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val scanedDevices by viewModel.scanedDevices.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val micAmplitudeData by viewModel.micAmplitudeData.collectAsState()
    val stateTxt by viewModel.stateTxt.collectAsState()
    val asrTxt by viewModel.asrTxt.collectAsState()

    var hasBluetoothConnectPermission by remember {
        mutableStateOf(permissionChecker.hasBluetoothConnectPermission())
    }
    val bluetoothConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasBluetoothConnectPermission = granted
            viewModel.start()
            viewModel.updatePairedDevice()
        }
    )
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
//            val allPermissionsGranted = permissions.entries.all { it.value }
//            if (allPermissionsGranted) {
//                viewModel.permissionGranted()
//            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val connectPermissionGranted =
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
                if (connectPermissionGranted) {
                    viewModel.start()
                    viewModel.updatePairedDevice()
                }
            }
        }
    )
    var isBluetoothEnabled by remember { mutableStateOf(permissionChecker.isBluetoothEnabled()) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            isBluetoothEnabled = true
            //Toast.makeText(context, "Bluetooth가 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            //Toast.makeText(context, "Bluetooth를 활성화해야 합니다.", Toast.LENGTH_SHORT).show()
        }
    }


    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {

        androidx.compose.material.Text(
            modifier = Modifier
                .padding(start = 15.dp, top = 10.dp),
            text = stateTxt,
            style = TextStyle(fontSize = 14.sp)
        )

        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val (conState, conBtn) = createRefs()
            Row(
                modifier = Modifier
                    .constrainAs(conState) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        id = when (headsetState) {
                            BluetoothHeadsetManager.HeadsetState.Connected -> R.drawable.ic_headset
                            BluetoothHeadsetManager.HeadsetState.Disconnected -> R.drawable.ic_headset_off
                            BluetoothHeadsetManager.HeadsetState.AudioActivated -> R.drawable.ic_headset_mic
                            BluetoothHeadsetManager.HeadsetState.Scanning -> R.drawable.ic_headset_off
                            else -> R.drawable.ic_headset
                        }
                    ),
                    contentDescription = "Connect State"
                )
                androidx.compose.material.Text(
                    modifier = Modifier
                        .padding(start = 5.dp),
                    text = when (headsetState) {
                        BluetoothHeadsetManager.HeadsetState.Connected -> "Connected"
                        BluetoothHeadsetManager.HeadsetState.Disconnected -> "Disconnected"
                        BluetoothHeadsetManager.HeadsetState.Scanning -> "Disconnected"
                        BluetoothHeadsetManager.HeadsetState.AudioActivated -> "AudioActivated"
                        BluetoothHeadsetManager.HeadsetState.AudioActivating -> "AudioActivating"
                        BluetoothHeadsetManager.HeadsetState.AudioActivationError -> "AudioActivationError"
                    },
                    style = TextStyle(fontSize = 14.sp)
                )
            }
            Button(
                modifier = Modifier
                    .constrainAs(conBtn) {
                        end.linkTo(parent.end)
                        linkTo(top = conState.top, bottom = conState.bottom)
                        width = Dimension.wrapContent
                        height = Dimension.value(35.dp)
                    }
                    .padding(end = 16.dp),
                onClick = {
                    when (headsetState) {
                        BluetoothHeadsetManager.HeadsetState.Disconnected -> {
                            if (Utils.hasBluetoothPermissions()) {
                                if (isBluetoothEnabled) { // 블루투스 활성화 체크
                                    viewModel.startScan()
                                } else {
                                    Utils.requestEnableBluetooth(enableBluetoothLauncher)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    bluetoothPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_SCAN
                                        )
                                    )
                                }
                            }

                        }
                        BluetoothHeadsetManager.HeadsetState.Connected -> viewModel.onClickDisconnect()
                        else -> {}
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = LillyBlue2,
                    contentColor = White
                ),
                shape = androidx.compose.material.MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 0.dp,
                    start = 25.dp,
                    end = 25.dp
                )
            ) {
                androidx.compose.material.Text(
                    text = when (headsetState) {
                        BluetoothHeadsetManager.HeadsetState.Connected -> "DISCONNECT"
                        BluetoothHeadsetManager.HeadsetState.Disconnected -> "SCAN"
                        BluetoothHeadsetManager.HeadsetState.Scanning -> "SCANNING..."
                        BluetoothHeadsetManager.HeadsetState.AudioActivated -> "DISCONNECT"
                        BluetoothHeadsetManager.HeadsetState.AudioActivating -> "DISCONNECT"
                        BluetoothHeadsetManager.HeadsetState.AudioActivationError -> "SCAN"
                    },
                    style = TextStyle(fontSize = 14.sp),
                    color = White
                )
            }
        }



        if (headsetState == BluetoothHeadsetManager.HeadsetState.Disconnected || headsetState == BluetoothHeadsetManager.HeadsetState.Scanning) {

            androidx.compose.material.Text(
                modifier = Modifier
                    .padding(start = 15.dp, top = 100.dp),
                text = "Paired Devices",
                style = TextStyle(fontSize = 14.sp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(10.dp)
                    .border(
                        width = 2.dp,
                        color = LillyPink2,
                        shape = RoundedCornerShape(5.dp) // Adjust the shape as needed
                    )
                    .padding(16.dp)
            ) {

                if (hasBluetoothConnectPermission) {
                    if (isBluetoothEnabled) { // 블루투스 활성화 체크
                        viewModel.updatePairedDevice()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        ) {
                            pairedDevices.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material.Text(
                                        text = item.name,
                                        style = androidx.compose.material.MaterialTheme.typography.body1
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { viewModel.connectDevice(item) },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = LillyPink3,
                                            contentColor = White
                                        ),
                                        shape = androidx.compose.material.MaterialTheme.shapes.medium,
                                        contentPadding = PaddingValues(
                                            top = 0.dp,
                                            bottom = 0.dp,
                                            start = 5.dp,
                                            end = 5.dp
                                        )
                                    ) {
                                        androidx.compose.material.Text(
                                            text = "CONNECT",
                                            style = TextStyle(fontSize = 11.sp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        BasicText(
                            text = "Bluetooth is disabled."
                        )
                        Button(
                            onClick = {
                                Utils.requestEnableBluetooth(enableBluetoothLauncher)
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = LillyPink3,
                                contentColor = White
                            )
                        ) {
                            androidx.compose.material.Text(text = "Enable Bluetooth")
                        }
                    }
                } else {
                    BasicText(
                        text = "Bluetooth permissions are required to display paired devices."
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                bluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = LillyPink3,
                            contentColor = White
                        )
                    ) {
                        androidx.compose.material.Text(text = "Request Permission")
                    }
                }


            }
            androidx.compose.material.Text(
                modifier = Modifier
                    .padding(start = 15.dp, top = 20.dp),
                text = "Available Devices",
                style = TextStyle(fontSize = 14.sp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(10.dp)
                    .border(
                        width = 2.dp,
                        color = LillyPink4,
                        shape = RoundedCornerShape(5.dp) // Adjust the shape as needed
                    )
                    .padding(16.dp)
            ) {
                if (hasBluetoothConnectPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        scanedDevices.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material.Text(
                                    text = item.name,
                                    style = androidx.compose.material.MaterialTheme.typography.body1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = { viewModel.connectDevice(item) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = LillyPink3,
                                        contentColor = White
                                    ),
                                    shape = androidx.compose.material.MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(
                                        top = 0.dp,
                                        bottom = 0.dp,
                                        start = 5.dp,
                                        end = 5.dp
                                    )
                                ) {
                                    androidx.compose.material.Text(
                                        text = "CONNECT",
                                        style = TextStyle(fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }


        } else {

            val requestPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // 권한이 허용된 경우 처리할 작업
                    // 음성 인식을 시작하는 코드를 여기에 추가할 수 있습니다.
                } else {
                    // 권한이 거부된 경우 사용자에게 설명을 보여줄 수도 있습니다.
                    // 예를 들어, SnackBar를 통해 권한 요청을 다시 시도하도록 안내할 수 있습니다.
                }
            }


            Button(
                modifier = Modifier
                    .padding(start=15.dp, top=10.dp),
                onClick = {
                    when {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // 이미 권한이 허용된 경우
                            if (!recording) {
                                viewModel.recordAudio()

                            } else {
                                viewModel.stopRecording()
                            }
                        }

                        else -> {
                            // 권한을 요청합니다.
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = LillyPink3,
                    contentColor = White
                ),
                shape = androidx.compose.material.MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 0.dp,
                    start = 5.dp,
                    end = 5.dp
                )
            ) {
                androidx.compose.material.Text(
                    text = when (recording) {
                        true -> "STOP"
                        false -> "Start Recording"
                    },
                    style = TextStyle(fontSize = 11.sp)
                )
            }


            WaveformVisualizer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(10.dp),
                amplitudes = micAmplitudeData,
                backgroundColor = LillyGray1,
                waveformColor = LillyBlue2
            )

            val scrollState = rememberScrollState()
            LaunchedEffect(asrTxt) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .weight(1f)
                    .border(
                        width = 2.dp,
                        color = LillyBlue1,
                        shape = RoundedCornerShape(5.dp) // Adjust the shape as needed
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(5.dp)
                ) {
                    androidx.compose.material.Text(
                        modifier = Modifier,
                        style = TextStyle(fontSize = 14.sp),
                        text = asrTxt
                    )
                }
            }


        }
    }

    if (inProgress) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = progressState,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(device = Devices.PIXEL_3A, showBackground = true, showSystemUi = true)
@Composable
fun DefaultPreview() {
    LillyTheme {
        MainView(viewModel= MainViewModelPreview(), permissionChecker = PreviewPermissionChecker())

    }
}

