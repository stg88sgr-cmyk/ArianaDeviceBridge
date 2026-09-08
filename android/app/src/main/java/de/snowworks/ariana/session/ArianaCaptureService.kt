package de.snowworks.ariana.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.snowworks.app.R
import de.snowworks.app.ui.DeviceGrantsActivity
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.Feature
import de.snowworks.ariana.camera.CameraFrameStore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service owning capture hardware. Each resource has an independent
 * start/stop action so stopping camera never leaves it open while microphone or
 * screen capture continues.
 */
class ArianaCaptureService : Service() {
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var cameraReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraWidth: Int = 0
    private var cameraHeight: Int = 0

    private val cameraFramePump = object : Runnable {
        override fun run() {
            val camera = cameraDevice ?: return
            val session = cameraSession ?: return
            val reader = cameraReader ?: return
            runCatching {
                val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.JPEG_QUALITY, CAMERA_JPEG_QUALITY)
                }.build()
                session.capture(request, null, cameraHandler)
            }
            cameraHandler?.postDelayed(this, CAMERA_FRAME_INTERVAL_MS)
        }
    }

    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private val micRunning = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var stoppingProjection = false
    private val pendingCapture = mutableSetOf<Feature>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_ALL) {
            shutdown()
            return START_NOT_STICKY
        }

        val gate = ArianaGate(this)
        if (!gate.isMasterEnabled || gate.isBlocked) {
            shutdown()
            return START_NOT_STICKY
        }

        val startingFeature = when (action) {
            ACTION_START_CAMERA -> Feature.CAMERA
            ACTION_START_MICROPHONE -> Feature.MICROPHONE
            ACTION_START_SCREEN -> Feature.SCREEN
            else -> null
        }
        if (startingFeature != null) {
            pendingCapture.add(startingFeature)
            // Android 14+ requires the matching foreground-service type before
            // sensitive capture resources (especially MediaProjection) open.
            refreshForegroundOrStop()
        }

        when (action) {
            ACTION_START_CAMERA -> startCamera()
            ACTION_STOP_CAMERA -> stopCamera()
            ACTION_START_MICROPHONE -> startMicrophone()
            ACTION_STOP_MICROPHONE -> stopMicrophone()
            ACTION_START_SCREEN -> startScreen(intent)
            ACTION_STOP_SCREEN -> stopScreen()
            ACTION_REFRESH -> Unit
            else -> Unit
        }

        refreshForegroundOrStop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseAllHardware()
        super.onDestroy()
    }

    private fun startCamera() {
        if (cameraDevice != null || cameraSession != null || SessionRegistry.isActive(Feature.CAMERA)) {
            pendingCapture.remove(Feature.CAMERA)
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCapture.remove(Feature.CAMERA)
            SessionRegistry.markActive(Feature.CAMERA, false)
            return
        }

        CameraFrameStore.clear()
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = runCatching { manager.cameraIdList.firstOrNull() }.getOrNull()
            ?: run {
                pendingCapture.remove(Feature.CAMERA)
                return
            }
        cameraThread = HandlerThread("ArianaCamera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)

        runCatching {
            manager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        configureCameraFrames(manager, id, camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        failCameraSession()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        failCameraSession()
                    }
                },
                cameraHandler,
            )
        }.onFailure {
            failCameraSession()
        }
    }

    private fun configureCameraFrames(manager: CameraManager, cameraId: String, camera: CameraDevice) {
        val sizes = runCatching {
            manager.getCameraCharacteristics(cameraId)
                .get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())

        val targetArea = 640L * 480L
        val size = sizes.minByOrNull { kotlin.math.abs(it.width.toLong() * it.height.toLong() - targetArea) }
            ?: android.util.Size(640, 480)
        cameraWidth = size.width
        cameraHeight = size.height

        val reader = ImageReader.newInstance(cameraWidth, cameraHeight, ImageFormat.JPEG, 2)
        cameraReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            image.use {
                val plane = it.planes.firstOrNull() ?: return@use
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                if (bytes.isNotEmpty()) {
                    CameraFrameStore.update(bytes, cameraWidth, cameraHeight)
                }
            }
        }, cameraHandler)

        runCatching {
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice !== camera) {
                            session.close()
                            return
                        }
                        cameraSession = session
                        pendingCapture.remove(Feature.CAMERA)
                        SessionRegistry.markActive(Feature.CAMERA, true)
                        cameraHandler?.removeCallbacks(cameraFramePump)
                        cameraHandler?.post(cameraFramePump)
                        refreshForegroundOrStop()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        failCameraSession()
                    }
                },
                cameraHandler,
            )
        }.onFailure {
            failCameraSession()
        }
    }

    private fun failCameraSession() {
        pendingCapture.remove(Feature.CAMERA)
        SessionRegistry.markActive(Feature.CAMERA, false)
        releaseCameraResources()
        refreshForegroundOrStop()
    }

    private fun stopCamera() {
        pendingCapture.remove(Feature.CAMERA)
        SessionRegistry.markActive(Feature.CAMERA, false)
        releaseCameraResources()
        CameraFrameStore.clear()
    }

    private fun releaseCameraResources() {
        cameraHandler?.removeCallbacks(cameraFramePump)
        runCatching { cameraSession?.stopRepeating() }
        runCatching { cameraSession?.abortCaptures() }
        runCatching { cameraSession?.close() }
        cameraSession = null
        runCatching { cameraReader?.close() }
        cameraReader = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        cameraWidth = 0
        cameraHeight = 0
        stopCameraThread()
    }

    private fun stopCameraThread() {
        val thread = cameraThread
        cameraHandler = null
        cameraThread = null
        thread?.quitSafely()
    }

    private fun startMicrophone() {
        if (audioRecord != null || SessionRegistry.isActive(Feature.MICROPHONE)) {
            pendingCapture.remove(Feature.MICROPHONE)
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCapture.remove(Feature.MICROPHONE)
            SessionRegistry.markActive(Feature.MICROPHONE, false)
            return
        }
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) {
            pendingCapture.remove(Feature.MICROPHONE)
            return
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            pendingCapture.remove(Feature.MICROPHONE)
            return
        }
        audioRecord = record
        runCatching { record.startRecording() }.onFailure {
            record.release()
            audioRecord = null
            pendingCapture.remove(Feature.MICROPHONE)
            return
        }
        pendingCapture.remove(Feature.MICROPHONE)
        SessionRegistry.markActive(Feature.MICROPHONE, true)
        micRunning.set(true)
        audioThread = Thread({
            val buffer = ShortArray(min / 2)
            while (micRunning.get()) {
                val current = audioRecord ?: break
                val read = runCatching { current.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read < 0) break
            }
        }, "ArianaMic").also { it.start() }
    }

    private fun stopMicrophone() {
        micRunning.set(false)
        runCatching { audioRecord?.stop() }
        audioThread?.join(300)
        audioThread = null
        audioRecord?.release()
        audioRecord = null
        pendingCapture.remove(Feature.MICROPHONE)
        SessionRegistry.markActive(Feature.MICROPHONE, false)
    }

    private fun startScreen(intent: Intent?) {
        if (projection != null || SessionRegistry.isActive(Feature.SCREEN)) {
            pendingCapture.remove(Feature.SCREEN)
            return
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: run {
            pendingCapture.remove(Feature.SCREEN)
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, data) ?: run {
            pendingCapture.remove(Feature.SCREEN)
            return
        }
        projection = mediaProjection
        stoppingProjection = false
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseScreenResources(stopProjection = false)
                    SessionRegistry.markActive(Feature.SCREEN, false)
                    if (!stoppingProjection) refreshForegroundOrStop()
                }
            },
            null,
        )

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ArianaScreen",
            width,
            height,
            density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null,
        )
        pendingCapture.remove(Feature.SCREEN)
        SessionRegistry.markActive(Feature.SCREEN, true)
    }

    private fun stopScreen() {
        stoppingProjection = true
        releaseScreenResources(stopProjection = true)
        pendingCapture.remove(Feature.SCREEN)
        SessionRegistry.markActive(Feature.SCREEN, false)
        stoppingProjection = false
    }

    private fun releaseScreenResources(stopProjection: Boolean) {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        if (stopProjection) runCatching { projection?.stop() }
        projection = null
    }

    private fun refreshForegroundOrStop() {
        val types = fgsTypes()
        if (types == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), types)
    }

    private fun fgsTypes(): Int {
        var types = 0
        val active = SessionRegistry.activeCaptureFeatures() + pendingCapture
        if (Feature.CAMERA in active) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (Feature.MICROPHONE in active) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (Feature.SCREEN in active) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        return types
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DeviceGrantsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ArianaCaptureService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val names = SessionRegistry.activeCaptureFeatures().joinToString(", ") { it.title }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(names.ifBlank { "Sitzung" })
            .setSmallIcon(R.drawable.ic_snowworks)
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_snowworks, getString(R.string.notif_stop), stop)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_session), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun releaseAllHardware() {
        stopCamera()
        stopMicrophone()
        stopScreen()
        SessionRegistry.clearCaptureFeatures()
    }

    private fun shutdown() {
        releaseAllHardware()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START_CAMERA = "de.snowworks.ariana.START_CAMERA"
        const val ACTION_STOP_CAMERA = "de.snowworks.ariana.STOP_CAMERA"
        const val ACTION_START_MICROPHONE = "de.snowworks.ariana.START_MICROPHONE"
        const val ACTION_STOP_MICROPHONE = "de.snowworks.ariana.STOP_MICROPHONE"
        const val ACTION_START_SCREEN = "de.snowworks.ariana.START_SCREEN"
        const val ACTION_STOP_SCREEN = "de.snowworks.ariana.STOP_SCREEN"
        const val ACTION_STOP_ALL = "de.snowworks.ariana.STOP_ALL"
        const val ACTION_REFRESH = "de.snowworks.ariana.REFRESH"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL_ID = "ariana_session"
        private const val NOTIF_ID = 42
        private const val SAMPLE_RATE = 16_000
        private const val CAMERA_FRAME_INTERVAL_MS = 750L
        private const val CAMERA_JPEG_QUALITY: Byte = 75

        fun start(context: Context, feature: Feature) {
            val action = when (feature) {
                Feature.CAMERA -> ACTION_START_CAMERA
                Feature.MICROPHONE -> ACTION_START_MICROPHONE
                else -> return
            }
            context.startForegroundService(Intent(context, ArianaCaptureService::class.java).setAction(action))
        }

        fun startWithProjection(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, ArianaCaptureService::class.java)
                    .setAction(ACTION_START_SCREEN)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }

        fun stopFeature(context: Context, feature: Feature) {
            val action = when (feature) {
                Feature.CAMERA -> ACTION_STOP_CAMERA
                Feature.MICROPHONE -> ACTION_STOP_MICROPHONE
                Feature.SCREEN -> ACTION_STOP_SCREEN
                else -> return
            }
            context.startService(Intent(context, ArianaCaptureService::class.java).setAction(action))
        }

        fun stopAll(context: Context) {
            context.startService(Intent(context, ArianaCaptureService::class.java).setAction(ACTION_STOP_ALL))
        }
    }
}
