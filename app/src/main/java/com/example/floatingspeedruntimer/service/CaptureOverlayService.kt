package com.example.floatingspeedruntimer.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.data.RectData
import com.example.floatingspeedruntimer.databinding.OverlayCaptureRegionBinding
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable

class CaptureOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var binding: OverlayCaptureRegionBinding
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    private var categoryNameForFile: String = ""

    // Callback para lidar com o ciclo de vida da MediaProjection
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            // A captura foi interrompida externamente, então o serviço deve parar.
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)!!
                
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val initialRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_INITIAL_RECT, RectData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_INITIAL_RECT) as? RectData
                }
                val mode = intent.getStringExtra(EXTRA_MODE)
                categoryNameForFile = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: "default"

                startMediaProjection(resultCode, data)
                showOverlay(initialRect, mode, intent.getStringExtra(EXTRA_SPLIT_ID))
            }
            ACTION_HIDE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        // Registra o callback imediatamente após criar a MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection?.registerCallback(mediaProjectionCallback, handler)
        }
    }

    private fun showOverlay(initialRectData: RectData?, mode: String?, splitId: String?) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = OverlayCaptureRegionBinding.inflate(LayoutInflater.from(this))
        overlayView = binding.root

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)

        initialRectData?.let {
            binding.resizableRectangle.setRectCoordinates(
                RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            )
        }

        binding.buttonCancel.setOnClickListener { stopSelf() }
        binding.buttonSaveRegion.setOnClickListener {
            val rect = binding.resizableRectangle.getRectCoordinates()
            val rectData = RectData(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            sendBroadcast(ACTION_REGION_SAVED, "region" to rectData)
            stopSelf()
        }

        if (mode == MODE_CAPTURE_SPLIT) {
            binding.buttonSaveRegion.visibility = View.GONE
            binding.buttonCaptureSplit.visibility = View.VISIBLE
            binding.buttonCaptureSplit.setOnClickListener {
                if (splitId != null) {
                    captureAndSaveSplitImage(splitId)
                }
            }
        }
    }

    private fun captureAndSaveSplitImage(splitId: String) {
        takeScreenshot { fullBitmap ->
            if (fullBitmap != null) {
                val rect = binding.resizableRectangle.getRectCoordinates()
                val croppedBitmap = Bitmap.createBitmap(
                    fullBitmap, rect.left.toInt(), rect.top.toInt(), rect.width().toInt(), rect.height().toInt()
                )
                fullBitmap.recycle()
                
                val path = saveBitmapToFile(croppedBitmap, splitId)
                croppedBitmap.recycle()
                
                sendBroadcast(ACTION_IMAGE_CAPTURED, "path" to path, "splitId" to splitId)
                stopSelf()
            } else {
                // Falha na captura, parar o serviço
                stopSelf()
            }
        }
    }
    
    private fun takeScreenshot(onBitmap: (Bitmap?) -> Unit) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    image.close()
                    onBitmap(bitmap)
                } else {
                    onBitmap(null)
                }
                this.setOnImageAvailableListener(null, null)
            }, handler)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Screenshot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }
    
    private fun saveBitmapToFile(bitmap: Bitmap, splitId: String): String {
        val dir = File(filesDir, "split_images")
        if (!dir.exists()) dir.mkdirs()
        val filename = "${categoryNameForFile}_${splitId}.png".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, filename)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
        }
        return file.absolutePath
    }

    private fun sendBroadcast(action: String, vararg extras: Pair<String, Serializable>) {
        val intent = Intent(action)
        extras.forEach { (key, value) -> intent.putExtra(key, value) }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Capture Service"
            val descriptionText = "Service for capturing screen regions for autosplitter"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Autosplitter Configuration")
            .setContentText("Defining capture region or images.")
            .setSmallIcon(R.drawable.ic_autosplit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
        virtualDisplay?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
        }
        mediaProjection?.stop()
        imageReader?.close()
        stopForeground(true)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "CaptureServiceChannel"
        private const val NOTIFICATION_ID = 2
        
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_HIDE = "ACTION_HIDE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_INITIAL_RECT = "EXTRA_INITIAL_RECT"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_SPLIT_ID = "EXTRA_SPLIT_ID"
        const val EXTRA_CATEGORY_NAME = "EXTRA_CATEGORY_NAME"

        const val MODE_SET_REGION = "MODE_SET_REGION"
        const val MODE_CAPTURE_SPLIT = "MODE_CAPTURE_SPLIT"

        const val ACTION_REGION_SAVED = "ACTION_REGION_SAVED"
        const val ACTION_IMAGE_CAPTURED = "ACTION_IMAGE_CAPTURED"
    }
}
