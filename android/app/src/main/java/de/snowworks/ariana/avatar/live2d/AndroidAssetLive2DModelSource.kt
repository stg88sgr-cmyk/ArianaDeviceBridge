package de.snowworks.ariana.avatar.live2d

import android.content.res.AssetManager

/** Reads Ariana Live2D exports from the app's packaged assets. */
class AndroidAssetLive2DModelSource(
    private val assets: AssetManager,
) : Live2DModelSource {

    override fun exists(path: String): Boolean = try {
        assets.open(path, AssetManager.ACCESS_STREAMING).use { }
        true
    } catch (_: Exception) {
        false
    }

    override fun readBytes(path: String): ByteArray =
        assets.open(path, AssetManager.ACCESS_STREAMING).use { it.readBytes() }
}
