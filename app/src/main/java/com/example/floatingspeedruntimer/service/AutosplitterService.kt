package com.example.floatingspeedruntimer.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
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

    // Configurações
    private val CAPTURE_INTERVAL_MS = 500L // Captura a tela 2x por segundo
    private val MATCH_THRESHOLD = 0.9 // 90% de confiança para considerar uma imagem encontrada

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
        
        splitsWithImages = category?.splits?.filter { !it.autoSplitImagePath.isNullOrEmpty() } ?: emptyList()
        if (splitsWithImages.isEmpty()) {
            // Nenhuma imagem configurada, o serviço não tem o que fazer
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
            // Todos os splits foram encontrados, serviço pode parar
            stopSelf()
            return
        }

        val screenshotBitmap = getScreenshot() ?: return
        val targetImagePath = splitsWithImages[currentSplitIndex].autoSplitImagePath!!

        // Converte o screenshot para o formato do OpenCV
        val screenMat = Mat()
        Utils.bitmapToMat(screenshotBitmap, screenMat)
        screenshotBitmap.recycle()
        Imgproc.cvtColor(screenMat, screenMat, Imgproc.COLOR_RGBA2RGB)

        // Carrega a imagem alvo do split
        val templateMat = Imgcodecs.imread(targetImagePath)
        if (templateMat.empty()) {
            // Imagem não encontrada, talvez pular para o próximo?
            return
        }

        // Garante que a imagem da tela seja maior que a imagem do template
        if (screenMat.rows() < templateMat.rows() || screenMat.cols() < templateMat.cols()) {
            screenMat.release()
            templateMat.release()
            return
        }

        // Realiza o reconhecimento (Template Matching)
        val resultWidth = screenMat.cols() - templateMat.cols() + 1
        val resultHeight = screenMat.rows() - templateMat.rows() + 1
        val result = Mat(resultHeight, resultWidth, CvType.CV_32FC1)
        Imgproc.matchTemplate(screenMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

        // Encontra o nível de confiança
        val minMaxResult = Core.minMaxLoc(result)
        val confidence = minMaxResult.maxVal

        if (confidence >= MATCH_THRESHOLD) {
            // IMAGEM ENCONTRADA!
            triggerSplit()
            currentSplitIndex++
        }

        // Libera a memória
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
        // Envia um comando para o TimerService fazer o split
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
