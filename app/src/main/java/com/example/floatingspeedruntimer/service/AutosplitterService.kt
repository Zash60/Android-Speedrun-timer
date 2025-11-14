package com.example.floatingspeedruntimer.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.RectData
import com.example.floatingspeedruntimer.data.Split
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class AutosplitterService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var splitsWithImages: List<Split>
    private var currentSplitIndex = 0
    private var isRunning = false

    // Configurações vindas da Category
    private lateinit var captureRegion: Rect
    private var matchThreshold: Double = 0.9

    // O loop pode ser mais rápido agora
    private val CAPTURE_INTERVAL_MS = 250L // 4x por segundo

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initDebug()) {
            // Lidar com falha ao carregar OpenCV
            stopSelf()
        }
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return START_NOT_STICKY
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: return START_NOT_STICKY
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: return START_NOT_STICKY

        val dataManager = DataManager.getInstance(this)
        val category = dataManager.findCategoryByName(dataManager.findGameByName(gameName), categoryName)

        // Carrega a configuração do AutoSplitter da categoria
        val regionData = category?.autoSplitterCaptureRegion
        if (category == null || !category.autoSplitterEnabled || regionData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        captureRegion = Rect(regionData.left, regionData.top, regionData.right, regionData.bottom)
        matchThreshold = category.autoSplitterThreshold
        splitsWithImages = category.splits.filter { !it.autoSplitImagePath.isNullOrEmpty() }
        
        if (splitsWithImages.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        setupImageReader()
        startCaptureLoop()

        return START_STICKY
    }

    private fun setupImageReader() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // O ImageReader ainda precisa capturar a tela inteira
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startCaptureLoop() {
        isRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning) {
                    performCheck()
                    handler.postDelayed(this, CAPTURE_INTERVAL_MS)
                }
            }
        })
    }

    private fun performCheck() {
        if (currentSplitIndex >= splitsWithImages.size) {
            stopSelf()
            return
        }

        val fullScreenshot = getScreenshot() ?: return
        
        // Corta o screenshot para a região de captura definida, com segurança
        val croppedBitmap = try {
            Bitmap.createBitmap(
                fullScreenshot,
                captureRegion.left,
                captureRegion.top,
                captureRegion.width(),
                captureRegion.height()
            )
        } catch (e: IllegalArgumentException) {
            // A região de captura está fora dos limites da tela, para o serviço
            stopSelf()
            return
        } finally {
            fullScreenshot.recycle()
        }
        
        val targetImagePath = splitsWithImages[currentSplitIndex].autoSplitImagePath!!
        val templateMat = Imgcodecs.imread(targetImagePath)

        if (templateMat.empty()) {
            return
        }

        val screenMat = Mat()
        Utils.bitmapToMat(croppedBitmap, screenMat)
        croppedBitmap.recycle()
        Imgproc.cvtColor(screenMat, screenMat, Imgproc.COLOR_RGBA2RGB)

        if (screenMat.rows() < templateMat.rows() || screenMat.cols() < templateMat.cols()) {
            screenMat.release()
            templateMat.release()
            return
        }
        
        val result = Mat()
        Imgproc.matchTemplate(screenMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)
        val minMaxResult = Core.minMaxLoc(result)

        if (minMaxResult.maxVal >= matchThreshold) {
            triggerSplit()
            currentSplitIndex++
        }

        screenMat.release()
        templateMat.release()
        result.release()
    }
    
    private fun getScreenshot(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        return bitmap
    }

    private fun triggerSplit() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_SPLIT_FROM_AUTOSPLITTER
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_GAME_NAME = "EXTRA_GAME_NAME"
        const val EXTRA_CATEGORY_NAME = "EXTRA_CATEGORY_NAME"
    }
}
