package com.headmetal.headwareintelligence

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class Work_list_Response(
    val work_list: List<String>
)

data class DeviceData(
    val name:String,
    val uuid:String,
    val address:String
)
@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Helmet(navController: NavController) {
    val context = LocalContext.current
    val auto: SharedPreferences =
        LocalContext.current.getSharedPreferences("autoLogin", Activity.MODE_PRIVATE)

    var helmetid by remember {
        mutableStateOf("")
    }

    var expanded by remember { mutableStateOf(false) }
    var itemOptions by remember { mutableStateOf(listOf<String>()) }
    var selectedOption by remember { mutableStateOf("") }
    val apiServiceWorklist =
        RetrofitInstance.apiService.API_work_list(id = auto.getString("userid", null).toString())
    apiServiceWorklist.enqueue(object : Callback<Work_list_Response> {
        override fun onResponse(
            call: Call<Work_list_Response>,
            response: Response<Work_list_Response>
        ) {
            if (response.isSuccessful) {
                response.body()?.let { workListResponse ->
                    itemOptions = workListResponse.work_list
                }
            }
        }
        override fun onFailure(call: Call<Work_list_Response>, t: Throwable) {
            Log.e("HEAD METAL", "Failed to fetch work list")
        }
    })
    
    // 권한 요청
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. Proceed with BLE operations.
        } else {
            // Permission is denied. Handle accordingly.
        }
    }

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    val isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }
    val bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
    val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    val scanList by remember { mutableStateOf(mutableStateListOf<DeviceData>()) }
    var showScanDialog by remember { mutableStateOf(false) }
    var showWIFIDialog by remember { mutableStateOf(false) }
    val SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb"
    val READ_UUID= "0000180a-0000-1000-8000-00805f9b34fb"
    // 스캔 콜백
    val scanCallback:ScanCallback=object:ScanCallback(){
        override fun onScanResult(callbackType:Int,result:ScanResult){
            Log.d("onScanResult", result.toString())
            if (ActivityCompat.checkSelfPermission(
                    navController.context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            if(result.device.name != null){
                var uuid ="null"
                if(result.scanRecord?.serviceUuids!=null){
                    uuid = result.scanRecord!!.serviceUuids.toString()
                }
                val scanItem = DeviceData(
                    result.device.name?:"null",
                    uuid,
                    result.device.address?:"null"
                )

                if(!scanList.contains(scanItem)){
                    scanList.add(scanItem)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d("HEAD METAL", "BLUETOOTH SCAN FAILED $errorCode")
        }
    }
    var readMsg by remember{ mutableStateOf("")}
    // 연결 콜백
    val gattCallback = object: BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if(newState == BluetoothProfile.STATE_CONNECTED){
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                Toast.makeText(context, gatt?.device?.name + "과 연결 성공",Toast.LENGTH_SHORT).show()
                gatt?.discoverServices()
            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Toast.makeText(context, gatt?.device?.name + "과 연결 해제",Toast.LENGTH_SHORT).show()
            }
        }

        // 데이터 보낼 때
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            when(status){
                BluetoothGatt.GATT_SUCCESS->{
                    Toast.makeText(context,"데이터 전송 성공",Toast.LENGTH_SHORT).show()
                }
                else->{
                    Toast.makeText(context,"데이터 전송 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 데이터 받을 때
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, "데이터 수신 성공", Toast.LENGTH_SHORT).show()
                readMsg = String(value)
                if(readMsg.startsWith("ui")){

                }
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S){
                Toast.makeText(context,"데이터 수신 성공",Toast.LENGTH_SHORT).show()
                if()
            }
        }
    }
    LaunchedEffect(Unit) {
        // 블루투스 기능 유무 체크
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            AlertDialog.Builder(navController.context)
                .setTitle("블루투스 연결 실패")
                .setMessage("본 기기는 블루투스를 지원하지 않습니다.")
                .setPositiveButton("확인") { dialog, which ->
                    navController.navigate("mainScreen")
                }
                .show()
        } else {
            // 블루투스 권한 체크
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                }

                else -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH)
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN)
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF9F9F9))
    {
        if(showScanDialog){
            AlertDialog(
                onDismissRequest = {
                    showScanDialog = false
                    bluetoothLeScanner.stopScan(scanCallback)
                },
                title = {Text(text = "헬멧 스캔 중")},
                text = {
                    Column{
                        if(scanList.isEmpty()){
                            Text("스캔된 디바이스가 없습니다.")
                        }
                        else{
                            scanList.forEach{device->
                                if(device.name.startsWith("HEADWARE")) {
                                    Button(onClick = {
                                        bluetoothAdapter.getRemoteDevice(device.address).connectGatt(context,false,gattCallback)
                                        showScanDialog = false
                                        showWIFIDialog = true},
                                        colors = ButtonDefaults.buttonColors(Color.Transparent)) {
                                        Text(text = device.name)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            bluetoothLeScanner.stopScan(scanCallback)
                            showScanDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(Color.Transparent)
                    ){
                        Text(text = "확인")
                    }
                }
            )
        }
        if(showWIFIDialog){
            AlertDialog(
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Default.ArrowBackIosNew,
                contentDescription = null,
                modifier = Modifier
                    .padding(20.dp)
                    .clickable {
                        navController.navigate("mainScreen")
                    }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "안전모 등록",
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
                Spacer(modifier = Modifier.width(125.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 15.5.dp)
            ) {
                Box(

                ) {
                    Column(
                    ) {

                        Row {
                            Text(
                                text = "작업자 정보",
                                color = Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        Row {

                            Text(
                                text = "작업자 ID : ",
                                color = Color.Black,
                                fontSize = 16.sp
                            )

                            Text(// 로그인 정보 연동 작업자 ID 출력
                                text = auto.getString("userid", null).toString(),
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Row {

                            Text(
                                text = "작업자 이름 : ",
                                color = Color.Black,
                                fontSize = 16.sp
                            )

                            Text(// 로그인 정보 연동 작업자 이름 출력
                                text = auto.getString("name", null).toString(),
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Spacer(
                            modifier = Modifier.height(30.dp)
                        )

                        Text(
                            text = "작업장 선택",
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                value = selectedOption,
                                onValueChange = {},
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                readOnly = true,
                                textStyle = TextStyle.Default.copy(fontSize = 15.sp)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                itemOptions.forEach { eachoption ->
                                    DropdownMenuItem(onClick = {
                                        selectedOption = eachoption
                                        expanded = false
                                    }) {
                                        Text(text = eachoption, fontSize = 15.sp)
                                    }
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(30.dp)
                        )

                        Row(
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "블루투스 상태 : ",
                                color = Color.Black,
                                fontSize = 20.sp
                            )
                            Text(
                                text = if (isBluetoothEnabled) "켜짐" else "꺼짐",
                                color = Color.Black,
                                fontSize = 20.sp
                            )
                        }

                        Row {
                            Button(
                                onClick = {
                                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        if (!bluetoothAdapter.isEnabled) {
                                            bluetoothAdapter.enable()
                                        } else {
                                            Toast.makeText(
                                                navController.context,
                                                "이미 블루투스가 켜져있습니다",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    else{
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    }
                                },
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                                colors = ButtonDefaults.buttonColors(Color(0xFFAA82B4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "켜기",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row {
                            Button(
                                onClick = {
                                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        if (bluetoothAdapter.isEnabled) {
                                            bluetoothAdapter.disable()
                                        } else {
                                            Toast.makeText(
                                                navController.context,
                                                "이미 블루투스가 꺼져있습니다",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    else{
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                                },
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                                colors = ButtonDefaults.buttonColors(Color(0xFFAA82B4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "끄기",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }


                        Spacer(
                            modifier = Modifier.height(20.dp)
                        )

                        Column(
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "안전모 번호",
                                color = Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextField(
                                value = helmetid,
                                onValueChange = { helmetid = it },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .alpha(0.6f)
                                    .width(350.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        Row {
                            Button(
                                onClick = {
                                    // 블루투스가 안 켜져 있을 경우
                                    if(!bluetoothAdapter.isEnabled){
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    }
                                    else{
                                        // 스캔 시작
                                        bluetoothLeScanner.startScan(null,scanSettings,scanCallback)
                                        showScanDialog = true
                                    }
                                },
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                                colors = ButtonDefaults.buttonColors(Color(0xFFAA82B4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "등록하기",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                        Row {
                            Button(
                                onClick = {},
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                                colors = ButtonDefaults.buttonColors(Color(0xFFAA82B4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "반납하기",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
