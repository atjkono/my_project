package com.example.test

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.test.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var mediaPlayer: MediaPlayer? = null

    private var previousBitmaps = mutableMapOf<Int, Bitmap>()
    private var lastDetectionTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.tvStatus.visibility = View.INVISIBLE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "カメラ初期化失敗: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close()
            return
        }

        val selectedCells = binding.overlay.getSelectedCells()
        var detected = false

        for ((index, rect) in selectedCells) {
            val scaledRect = Rect(
                rect.left * bitmap.width / binding.overlay.width,
                rect.top * bitmap.height / binding.overlay.height,
                rect.right * bitmap.width / binding.overlay.width,
                rect.bottom * bitmap.height / binding.overlay.height
            )

            try {
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    scaledRect.left,
                    scaledRect.top,
                    scaledRect.width(),
                    scaledRect.height()
                )

                val prev = previousBitmaps[index]
                if (prev != null) {
                    val diff = calculateBitmapDifference(prev, cropped)
                    if (diff > PIXEL_DIFF_THRESHOLD) {
                        detected = true
                    }
                }
                previousBitmaps[index] = cropped
            } catch (e: Exception) {
                Log.e("BitmapError", "Cropping failed: ${e.message}")
            }
        }

        runOnUiThread {
            if (detected && System.currentTimeMillis() - lastDetectionTime > 3000) {
                lastDetectionTime = System.currentTimeMillis()
                binding.tvStatus.text = "移動を検知"
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.postDelayed({
                    binding.tvStatus.visibility = View.INVISIBLE
                }, 2000)
                playAlarm()
            }
        }

        imageProxy.close()
    }

    private fun calculateBitmapDifference(b1: Bitmap, b2: Bitmap): Int {
        val w = b1.width.coerceAtMost(b2.width)
        val h = b1.height.coerceAtMost(b2.height)
        var diff = 0L

        for (y in 0 until h step 5) {
            for (x in 0 until w step 5) {
                val c1 = b1.getPixel(x, y)
                val c2 = b2.getPixel(x, y)

                val r1 = Color.red(c1)
                val g1 = Color.green(c1)
                val b1c = Color.blue(c1)

                val r2 = Color.red(c2)
                val g2 = Color.green(c2)
                val b2c = Color.blue(c2)

                diff += abs(r1 - r2) + abs(g1 - g2) + abs(b1c - b2c)
            }
        }

        return (diff / ((w / 5) * (h / 5))).toInt()
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun playAlarm() {
        if (mediaPlayer?.isPlaying == true) return
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm)
        mediaPlayer?.start()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "カメラの許可が必要です", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaPlayer?.release()
    }

    companion object {
        private const val PIXEL_DIFF_THRESHOLD = 30
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
