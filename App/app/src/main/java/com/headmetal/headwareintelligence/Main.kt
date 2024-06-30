package com.headmetal.headwareintelligence

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeatherResponse(
    val temperature: Float,
    val airVelocity: Float,
    val precipitation: Float,
    val humidity: Float
)

@Composable
fun Main(navController: NavController = rememberNavController()) {
    BackOnPressed()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F9F9)
    ) { MainComposable(navController = navController) }
}

@Composable
fun MainComposable(navController: NavController = rememberNavController()) {
    val sharedAccount: SharedPreferences =
        LocalContext.current.getSharedPreferences("Account", Activity.MODE_PRIVATE)
    val userName = sharedAccount.getString("name", null)
    val type = sharedAccount.getString("type", null)

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val locationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    var temperature by remember { mutableFloatStateOf(0.0f) }
    var airVelocity by remember { mutableFloatStateOf(0.0f) }
    var precipitation by remember { mutableFloatStateOf(0.0f) }
    var humidity by remember { mutableFloatStateOf(0.0f) }

    val refreshState: MutableState<Boolean> = remember { mutableStateOf(false) }

    LaunchedEffect(refreshState.value) {
        when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasLocationPermission = true
            }

            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        if (hasLocationPermission) {
            val location = fusedLocationClient.lastLocation.await()
            location?.let { pos ->
                RetrofitInstance.apiService.getWeather(pos.latitude, pos.longitude).enqueue(object :
                    Callback<WeatherResponse> {
                    override fun onResponse(
                        call: Call<WeatherResponse>,
                        response: Response<WeatherResponse>
                    ) {
                        if (response.isSuccessful) {
                            val weather: WeatherResponse? = response.body()
                            weather?.let {
                                temperature = it.temperature
                                airVelocity = it.airVelocity
                                precipitation = it.precipitation
                                humidity = it.humidity
                            }

                            if (refreshState.value) {
                                Toast
                                    .makeText(
                                        navController.context,
                                        "새로고침 되었습니다.",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                                refreshState.value = false
                            }

                            Log.d("HEAD METAL", "날씨 정보 로딩 성공")
                        }
                    }

                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                        Log.e("HEAD METAL", "서버 통신 실패: ${t.message}")
                    }
                })
            }
        } else {
            Log.e("HEAD METAL", "위치 권한이 필요함")
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DrawerMenuIcon(
            modifier = Modifier.clickable { navController.navigate("MenuScreen") },
            imageVector = Icons.Default.Menu
        )
        WelcomeUserComposable(userName = userName)
        MainFunctionButtonMenu(type = type, navController = navController)
        MainContentsHeader(refreshState = refreshState)

        //


        val weatherInfo: String
        val weatherIcon: ImageVector
        val weatherColor: Color

        if (precipitation > 30) {
            weatherInfo = "호우 경보"
            weatherIcon = Icons.Default.Water
            weatherColor = Color(0xFF00BFFF)
        } else if (precipitation > 20) {
            weatherInfo = "호우 주의보"
            weatherIcon = Icons.Default.Water
            weatherColor = Color(0xFF00BFFF)
        } else if (precipitation > 0) {
            weatherInfo = "비"
            weatherIcon = Icons.Default.WaterDrop
            weatherColor = Color(0xFF00BFFF)
        } else {
            weatherInfo = "맑음"
            weatherIcon = Icons.Default.WbSunny
            weatherColor = Color(0xFFFF7F00)
        }
        Box(
            modifier = Modifier
                .background(color = Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(8.dp)
                )
                .fillMaxWidth()
        ) {
            Row {
                Icon(
                    imageVector = weatherIcon,
                    contentDescription = "날씨",
                    tint = weatherColor,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 10.dp)
                        .size(40.dp)
                )
                Column {
                    Text(
                        text = "기상 정보 : $weatherInfo",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(
                            start = 10.dp, top = 10.dp, bottom = 10.dp
                        )
                    )
                    Text(
                        text = "1시간 강수량 : " + precipitation.toString() + "mm",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
                    )
                    Text(
                        text = "기온 : " + temperature.toString() + "ºC" + if (temperature > 35) {
                            "(폭염 경보)"
                        } else if (temperature > 33) {
                            "(폭염 주의보)"
                        } else {
                            ""
                        },
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
                    )
                    Text(
                        text = "풍속 : " + airVelocity.toString() + "m/s" + if (airVelocity > 21) {
                            "(강풍 경보)"
                        } else if (airVelocity > 14) {
                            "(강풍 주의보)"
                        } else {
                            ""
                        },
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
                    )
                    Text(
                        text = "습도 : " + humidity.toString() + "%",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
                    )
                }
            }
        }
        Box(modifier = Modifier
            .padding(top = 8.dp)
            .background(color = Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(8.dp)
            )
            .fillMaxWidth()
            .clickable { navController.navigate("CountermeasureScreen") }
        ) {
            Row {
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = "주의 행동 요령",
                    tint = Color(0xFFFFCC00),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 10.dp, top = 25.dp, bottom = 25.dp)
                        .size(40.dp)
                )
                Text(
                    text = "주의 행동 요령",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 30.dp)
                )
            }
        }
        if (type == "manager") {
            Box(modifier = Modifier
                .padding(top = 8.dp)
                .background(color = Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(8.dp)
                )
                .fillMaxWidth()
                .clickable { navController.navigate("ProcessingScreen") }
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 10.dp, top = 25.dp, bottom = 25.dp)
                            .size(40.dp)
                    )
                    Text(
                        text = "사고 처리 내역",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeUserComposable(
    userName: String?
) {
    Box(modifier = Modifier.padding(top = 26.dp)) {
        Column {
            WelcomeMessage()
            WelcomeUserName(userName)
        }
    }
}

@Composable
fun WelcomeMessage() {
    Text(
        text = "반갑습니다,",
        fontSize = 16.sp
    )
}

@Composable
fun WelcomeUserName(
    userName: String?
) {
    userName?.let { name ->
        Text(
            text = name + "님",
            fontSize = 24.sp
        )
    }
}

@Composable
fun MainFieldLabel(
    text: String = ""
) {
    FieldLabel(
        text = text,
        fontSize = 18.sp
    )
}

@Composable
fun MainFunctionButton(
    buttonText: String = "",
    colors: ButtonColors,
    onClick: () -> Unit
) {
    FunctionButton(
        modifier = Modifier.fillMaxWidth(),
        buttonText = buttonText,
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
        onClick = onClick
    )
}

@Composable
fun MainFunctionButtonMenu(
    type: String?,
    navController: NavController = rememberNavController()
) {
    Column(Modifier.padding(top = 30.dp)) {
        if (type == "manager") {
            MainFieldLabel(text = "관리자 기능")
            MainFunctionButton(
                buttonText = "사고 추세 확인",
                colors = ButtonDefaults.buttonColors(Color(0xFF99CCFF)),
            ) { navController.navigate("TrendScreen") }
            MainFunctionButton(
                buttonText = "사고 발생지 확인",
                colors = ButtonDefaults.buttonColors(Color(0xFFFF6600)),
            ) { navController.navigate("MapScreen") }
            MainFunctionButton(
                buttonText = "미처리 사고 발생지 확인",
                colors = ButtonDefaults.buttonColors(Color(0xFFFF8000)),
            ) { navController.navigate("NullMapScreen") }
            MainFunctionButton(
                buttonText = "작업장 관리",
                colors = ButtonDefaults.buttonColors(Color(0xFFFF8000)),
            ) { navController.navigate("WorkListScreen") }
        } else {
            MainFieldLabel(text = "근로자 기능")
            MainFunctionButton(
                buttonText = "안전모 등록",
                colors = ButtonDefaults.buttonColors(Color(0xFFFFB266)),
            ) { navController.navigate("HelmetScreen") }
        }
    }
}

@Composable
fun MainContentsHeader(
    refreshState: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val coroutineScope = rememberCoroutineScope()
    var isRefreshClickable by remember { mutableStateOf(true) }

    Row(modifier = Modifier.padding(top = 30.dp)) {
        FieldLabel(
            text = "정보",
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        ClickableIcon(
            modifier = Modifier.clickable(enabled = isRefreshClickable) {
                refreshState.value = true
                isRefreshClickable = false
                coroutineScope.launch {
                    delay(3000)
                    isRefreshClickable = true
                }
            },
            imageVector = Icons.Default.Update
        )
    }
}

// 프리뷰
@Preview(showBackground = true)
@Composable
fun MainPreview() {
    Main()
}

@Preview(showBackground = true)
@Composable
fun ClickableIconPreview() {
    ClickableIcon(imageVector = Icons.Default.Menu)
}

@Preview(showBackground = true)
@Composable
fun DrawerMenuIconPreview() {
    DrawerMenuIcon(imageVector = Icons.Default.Menu)
}

@Preview(showBackground = true)
@Composable
fun WelcomeUserComposablePreview() {
    WelcomeUserComposable(userName = "사용자")
}

@Preview(showBackground = true)
@Composable
fun WelcomeMessagePreview() {
    WelcomeMessage()
}

@Preview(showBackground = true)
@Composable
fun WelcomeUserNamePreview() {
    WelcomeUserName(userName = "사용자")
}

@Preview(showBackground = true)
@Composable
fun MainFunctionButtonMenuPreview() {
    MainFunctionButtonMenu(type = "manager")
}
