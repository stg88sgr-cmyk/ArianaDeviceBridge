package de.snowworks.ariana.camera

/**
 * Keeps only the newest camera JPEG in process memory. Nothing is persisted,
 * uploaded, or restored after process death.
 */
object CameraFrameStore {
    data class Frame(
        val jpeg: ByteArray,
        val width: Int,
        val height: Int,
        val capturedAt: Long,
    )

    @Volatile
    private var latestFrame: Frame? = null

    fun update(jpeg: ByteArray, width: Int, height: Int, capturedAt: Long = System.currentTimeMillis()) {
        latestFrame = Frame(jpeg.copyOf(), width, height, capturedAt)
    }

    fun latest(): Frame? = latestFrame?.let {
        Frame(it.jpeg.copyOf(), it.width, it.height, it.capturedAt)
    }

    fun hasFrame(): Boolean = latestFrame != null

    fun clear() {
        latestFrame = null
    }
}
