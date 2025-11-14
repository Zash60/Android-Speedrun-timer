package com.example.floatingspeedruntimer.service

import android.app.Activity
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)!!
                val initialRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_INITIAL_RECT, RectData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_INITIAL_RECT) as? RectData
                }
                val mode = intent.getStringExtra(EXTRA_MODE)

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
                    var bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    // A bitmap pode conter a barra de status, etc. Precisamos cortar.
                    // Para simplificar, estamos retornando a bitmap inteira.
                    // Uma implementação mais robusta poderia obter a posição da janela.
                    
                    image.close()
                    onBitmap(bitmap)
                } else {
                    onBitmap(null)
                }
                // Limpa o listener para não ser chamado novamente
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
        // Usa o nome da categoria + nome do split para evitar conflitos
        val filename = "${intent?.getStringExtra("CATEGORY_NAME")}_${splitId}.png".replace(" ", "_")
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

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_HIDE = "ACTION_HIDE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_INITIAL_RECT = "EXTRA_INITIAL_RECT"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_SPLIT_ID = "EXTRA_SPLIT_ID"
        const val EXTRA_CATEGORY_NAME = "CATEGORY_NAME"

        const val MODE_SET_REGION = "MODE_SET_REGION"
        const val MODE_CAPTURE_SPLIT = "MODE_CAPTURE_SPLIT"

        const val ACTION_REGION_SAVED = "ACTION_REGION_SAVED"
        const val ACTION_IMAGE_CAPTURED = "ACTION_IMAGE_CAPTURED"
    }
}
