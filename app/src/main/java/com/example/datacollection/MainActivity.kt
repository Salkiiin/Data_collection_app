package com.example.datacollection

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollection.ui.theme.DataCollectionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


enum class Direction{
    indicator_off,
    indicator_left,
    indicator_right
}
private val accelerometerReading = FloatArray(3)


class CollectionService : Service(), SensorEventListener {
    override fun onSensorChanged(event: SensorEvent?){

        if (event == null){
            return
        }
        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        }

    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}

//private lateinit var sensorManager: SensorManager

class MainActivity : ComponentActivity() {
    private val viewModel: MyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        var dataCollector = CollectionService()
        // 1
        var sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // 2
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(dataCollector, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }

        super.onCreate(savedInstanceState)
        setContent {
            DataCollectionTheme {
                    OrganizeFrames(modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                        .padding(16.dp),
                        this,
                        viewModel
                    )
            }
        }
    }
}


@Composable
fun OrganizeFrames(modifier: Modifier = Modifier, context: Context, viewModel: MyViewModel){
    var isRunning by remember { mutableStateOf(false)}
    isRunning = if(isRunning){
        RunningPage(modifier, context, viewModel)
    }else{
        StartPage(modifier)
    }
}

@Preview
@Composable
fun StartPage(modifier: Modifier = Modifier): Boolean {
    var isRunning by remember { mutableStateOf(false)}
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,

    ) {
        Button(onClick = { isRunning = true },modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            Text(stringResource(R.string.start), fontSize = 64.sp)
        }
    }
    return isRunning
}

@Composable
fun RunningPage(modifier: Modifier = Modifier, context: Context, viewModel: MyViewModel): Boolean {
    var isRunning by remember { mutableStateOf(true)}
    var indicatorstate:Direction by remember{mutableStateOf(Direction.indicator_off)}
    val intent = Intent(context, CollectionService::class.java)
    val measurements = viewModel.measurements.value.orEmpty()
    context.startService(intent)



    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when(indicatorstate){
        Direction.indicator_off -> Text("X", fontSize = 128.sp)
        Direction.indicator_left -> Text("<", fontSize = 128.sp)
        Direction.indicator_right -> Text(">", fontSize = 128.sp)
    }
        //var printthis = accelerometerReading.contentToString()
        Text(
            "Accelerometer Readings: ${
                if (measurements.isNotEmpty()) measurements[measurements.size - 1].contentToString() else "No readings"
            }",
            fontSize = 32.sp
        )




        Row(modifier = Modifier
            .weight(5f)
            .fillMaxSize()
            .padding(16.dp)){
            Button(onClick = { indicatorstate = Direction.indicator_left },modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)) {
                Text("<-", fontSize = 64.sp)

            }
            Button(onClick = { indicatorstate = Direction.indicator_right},modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)) {
                Text("->", fontSize = 64.sp)
            }
        }
            Button(
                onClick = { indicatorstate = Direction.indicator_off }, modifier = Modifier
                    .weight(3f)
                    .fillMaxWidth()
                    .padding(16.dp)) {
                Text("OFF", fontSize = 64.sp)
            }
            Button(onClick = { isRunning = false }, modifier = Modifier
                .weight(3f)
                .fillMaxWidth()
                .padding(16.dp)) {
                Text(stringResource(R.string.stop), fontSize = 64.sp)
        }
    }
    return isRunning
}
class MyViewModel : ViewModel() {
    private val _measurements = MutableLiveData<List<FloatArray>>()
    val measurements: LiveData<List<FloatArray>> get() = _measurements

    init {
        viewModelScope.launch {
            val measurementsList = mutableListOf<FloatArray>()
            while (true) {
                measurementsList.add(accelerometerReading.clone())
                _measurements.postValue(measurementsList.toList())
                delay(500)
            }
        }
    }
}