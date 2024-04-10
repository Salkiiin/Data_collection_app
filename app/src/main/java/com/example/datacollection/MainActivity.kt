package com.example.datacollection

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.datacollection.ui.theme.DataCollectionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.ExecutionException
import kotlin.collections.ArrayList
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow


fun zipFolder(sourceFolderPath: String, zipFilePath: String) {
    val sourceFolder = File(sourceFolderPath)
    val zipFile = File(zipFilePath)

    ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
        zipFolderInternal(sourceFolder, sourceFolder, zipOutputStream)
    }
    Log.d("Compression", "Folder successfully compressed: $zipFilePath")
}

private fun zipFolderInternal(
    sourceFolder: File,
    currentFolder: File,
    zipOutputStream: ZipOutputStream
) {
    val fileList = currentFolder.listFiles() ?: return

    for (file in fileList) {
        if (file.isDirectory) {
            zipFolderInternal(sourceFolder, file, zipOutputStream)
        } else {
            val entryRelativePath = file.toRelativeString(sourceFolder)
            val zipEntry = ZipEntry(entryRelativePath)
            zipOutputStream.putNextEntry(zipEntry)

            val inputStream = FileInputStream(file)
            inputStream.copyTo(zipOutputStream, bufferSize = 1024)

            inputStream.close()
            zipOutputStream.closeEntry()
        }
    }
}

class MyViewModelFactory(
    private val application: Application,
    private val lifecycleOwner: LifecycleOwner
) : ViewModelProvider.Factory{

    override

    fun

            <T : ViewModel>

            create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyViewModel::class.java)) {
            return MyViewModel(application, lifecycleOwner) as T
        } else {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}


private const val TAG = "Camera"
private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

fun takePicture(context: Context, lifecycleOwner: LifecycleOwner, imageCapture: ImageCapture): String {

    // Create output directory
    val outputDirectory = getOutputDirectory(context)
    val photoFile = File(
        outputDirectory,
        SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    try{
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    Log.d(TAG, "Image captured: $savedUri")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturing image: ${exception.message}", exception)
                }
            }
        )
    }catch(e: Exception){
        Log.e(TAG, "imageCapture error:", e)
    }

    return photoFile.absolutePath
}

private fun getOutputDirectory(context: Context): File {
    val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else context.filesDir
}


enum class Direction{
    indicator_off,
    indicator_left,
    indicator_right
}
private val accelerometerReading = FloatArray(4)
private var direction = 0
private var isRunning = false
private var _isSaving = MutableStateFlow(false)
val isSaving: StateFlow<Boolean> get () = _isSaving

class CollectionService : Service(), SensorEventListener {
    override fun onSensorChanged(event: SensorEvent?){

        if (event == null){
            return
        }
        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size-1)
            accelerometerReading[3] = direction.toFloat()

        }

    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}


private const val CAMERA_PERMISSION_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {
    val viewModel: MyViewModel by viewModels {MyViewModelFactory(application, this)}

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
                        context = this,
                        activity = this,
                        viewModel
                    )
            }
        }
    }
}


@Composable
fun OrganizeFrames(modifier: Modifier = Modifier, context: Context, activity: ComponentActivity, viewModel: MyViewModel){
    var isRunningChecking by remember { mutableStateOf(false)}
    Log.d("isRunningState", "Current state of isRunning is: $isRunning")

    isRunningChecking = if(isRunning){
        viewModel.bindCamera()
        RunningPage(modifier, context, viewModel)
    }else{
        StartPage(modifier, context, activity, viewModel)
    }
}


@Composable
fun StartPage(modifier: Modifier = Modifier, context: Context, activity: ComponentActivity, viewModel: MyViewModel): Boolean {
    var isRunningChecking by remember { mutableStateOf(false)}
    val isSavingFlow = MutableStateFlow(_isSaving.value)

    LaunchedEffect(key1 = Unit) {
        while (true) {
            // Update isSaving based on the actual state from viewModel
            val currentSaving = isSaving.value
            if (currentSaving != isSavingFlow.value) {
                isSavingFlow.value = currentSaving
            }
            delay(1000)
        }
    }
val isSaving by isSavingFlow.collectAsState()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,

    ) {
        if(isSaving){
                Text(text = "Saving in progress...\nPlease wait!", fontSize = 32.sp)
        }else{
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    isRunningChecking = true
                    isRunning = isRunningChecking
                    Log.d("isRunningState", "State should now be true....isRunningChecking: $isRunningChecking isRunning: $isRunning")

                } else {
                    // Request camera permission
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                }
            },modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                Text(stringResource(R.string.start), fontSize = 64.sp)
            }
        }


    }

    return isRunningChecking
}

@Composable
fun RunningPage(modifier: Modifier = Modifier, context: Context, viewModel: MyViewModel): Boolean {

    var isRunningChecking by remember { mutableStateOf(true)}
    var indicatorstate:Direction by remember{mutableStateOf(Direction.indicator_off)}
    val intent = Intent(context, CollectionService::class.java)
    val measurements = viewModel.measurements.value.orEmpty()
    context.startService(intent)


    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (indicatorstate) {
            Direction.indicator_off -> Text("X", fontSize = 128.sp)
            Direction.indicator_left -> Text("<", fontSize = 128.sp)
            Direction.indicator_right -> Text(">", fontSize = 128.sp)
        }


        Row(
            modifier = Modifier
                .weight(5f)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(
                onClick = { indicatorstate = Direction.indicator_left }, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text("<-", fontSize = 64.sp)

            }
            Button(
                onClick = { indicatorstate = Direction.indicator_right }, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text("->", fontSize = 64.sp)
            }
        }
        Button(
            onClick = { indicatorstate = Direction.indicator_off }, modifier = Modifier
                .weight(3f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("OFF", fontSize = 64.sp)
        }
        direction = indicatorstate.ordinal
        Button(
            onClick = {
                isRunningChecking = false
                isRunning = isRunningChecking
                viewModel.stopDataCollection()
            }, modifier = Modifier
                .weight(3f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.stop), fontSize = 64.sp)
        }
    }
    return isRunningChecking
}
class MyViewModel(application: Application, private val lifecycleOwner: LifecycleOwner) : AndroidViewModel(application) {
    private val _measurements = MutableLiveData<List<FloatArray>>()
    private val pathList = mutableListOf<String>()
    private var cameraProvider: ProcessCameraProvider? = null
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setJpegQuality(1)
        .build()
    val measurements: LiveData<List<FloatArray>> get() = _measurements

    init {
        viewModelScope.launch {


            // Set up camera provider
            val cameraProviderFuture = ProcessCameraProvider.getInstance(application)
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                Log.e(TAG, "Error getting camera provider: ${e.message}", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error getting camera provider: ${e.message}", e)
            }
            pictureIfRunning()
        }
    }


    override fun onCleared() {
        super.onCleared()
        cameraProvider?.unbindAll()
        Log.d("DataCollectionLogging", "onCleared called")
    }
    private suspend fun pictureIfRunning(){
        val measurementsList = mutableListOf<FloatArray>()
        while(true){
            while(!isRunning){
                delay(100)
                Log.d(TAG, "Camera should stop")
            }
            while (isRunning) {
                Log.d(TAG, "Camera should take pictures now")
                val picturePath = takePicture(getApplication(), lifecycleOwner, imageCapture)
                pathList.add(picturePath)
                measurementsList.add(accelerometerReading.clone())
                _measurements.postValue(measurementsList.toList())
                delay(100)
            }
        }
    }
    fun bindCamera(){
        try {
            // Bind use cases to camera
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                //preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }
    private suspend fun saveDataToCsv() {
        withContext(Dispatchers.IO) {
            try {
                val csvFile = File(pathList[0].substringBeforeLast('/'), "data.csv")
                val csvWriter = FileWriter(csvFile)

                // Write header
                csvWriter.write("X,Y,Z,Indicator,ImagePath\n")

                val measurementsCopy = ArrayList(measurements.value)
                val pathListCopy = ArrayList(pathList)
                // Write measurements
                for (i in 0 until measurementsCopy.size) {
                    val measurement = measurementsCopy[i]
                    csvWriter.write("${measurement[0]},${measurement[1]},${measurement[2]},${measurement[3]},${pathListCopy[i]}\n")
                }
                Log.d("DataCollectionLogging", "Data saved to CSV file. Path: ${csvFile.absolutePath}")
                csvWriter.close()
            } catch (e: IOException) {
                // Handle IOException
                Log.e("DataCollectionLogging", "Error saving data to CSV file: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    private suspend fun zipData(){
        val zipFilePath = "/storage/emulated/0/Download/sent_me_to_Niklas_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".zip"
        val sourceFolderPath = pathList[0].substringBeforeLast('/')
        Log.e("ZipTag", "zipFilePath: $zipFilePath, sourceFolderPath: $sourceFolderPath")
        zipFolder(sourceFolderPath, zipFilePath)

        for(file in File(sourceFolderPath).listFiles()!!){
            file.delete()
        }
        _isSaving.value = false
        Toast.makeText(getApplication(), "Data saved, the app may now be closed", Toast.LENGTH_SHORT).show()
    }
    fun stopDataCollection() {
        viewModelScope.launch {
            _isSaving.value = true
            Toast.makeText(getApplication(), "please wait, saving in progress...", Toast.LENGTH_SHORT).show()
            saveDataToCsv()
            zipData()
            onCleared()
        }
    }
    class Factory(
        private val application: Application,
        private val lifecycleOwner: LifecycleOwner
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MyViewModel(application, lifecycleOwner) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}
