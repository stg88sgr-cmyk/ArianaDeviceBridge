package de.snowworks.ariana.avatar.live2d

/**
 * Minimal source abstraction for Ariana Live2D assets.
 *
 * Keeping the source independent from Cubism lets the install pipeline inspect
 * model bundles from Android assets, tests, or another local store without
 * coupling validation to renderer implementation details.
 */
interface Live2DModelSource {
    fun exists(path: String): Boolean

    fun readBytes(path: String): ByteArray

    fun readText(path: String): String = readBytes(path).toString(Charsets.UTF_8)

    /**
     * Returns only bundle files that are actually present in this source.
     * The result can be passed directly to [Live2DInstallGate.Candidate].
     */
    fun existingPaths(bundle: Live2DModelBundle): Set<String> =
        bundle.requiredPaths().filterTo(linkedSetOf(), ::exists)
}

/** All files that belong to this exported model bundle. */
fun Live2DModelBundle.requiredPaths(): Set<String> = linkedSetOf<String>().apply {
    add(modelJsonPath)
    add(mocPath)
    add(physicsPath)
    add(cdiPath)
    addAll(texturePaths)
    addAll(expressionPaths.values)
}
