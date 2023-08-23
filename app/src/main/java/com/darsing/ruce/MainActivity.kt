package com.darsing.ruce

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var audioRecord: AudioRecord? = null
    private val requestCode = 101
    companion object {
        private const val ANOTHER_REQUEST_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.FOREGROUND_SERVICE), ANOTHER_REQUEST_CODE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), requestCode)
        } else {
            initResources()
        }

        // To start the service
        startService(Intent(this, MyForegroundService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initResources()
            }
        }
    }

    private fun initResources() {
        // Initialize camera
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val smallestSize = map.getOutputSizes(SurfaceTexture::class.java).minByOrNull { it.height * it.width }


            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
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
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCameraSession(camera, smallestSize!!)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice?.close()
                    cameraDevice = null
                }

                override fun onClosed(camera: CameraDevice) {
                    cameraDevice = null
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        // Initialize microphone
        val minSampleRate = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minSampleRate)
        audioRecord?.startRecording()
    }

    private fun startCameraSession(camera: CameraDevice, size: Size) {
        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.setDefaultBufferSize(size.width, size.height)
        val previewSurface = Surface(surfaceTexture)

        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(previewSurface)

        camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                val captureRequest = captureRequestBuilder.build()
                session.setRepeatingRequest(captureRequest, null, null)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release resources
        audioRecord?.stop()
        audioRecord?.release()
        cameraDevice?.close()
        // To stop the service
        stopService(Intent(this, MyForegroundService::class.java))
    }
}
