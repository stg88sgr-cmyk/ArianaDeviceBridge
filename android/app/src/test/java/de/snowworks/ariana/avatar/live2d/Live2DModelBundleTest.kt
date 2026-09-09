package de.snowworks.ariana.avatar.live2d

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DModelBundleTest {

    @Test
    fun completeArianaBundlePasses() {
        val bundle = Live2DModelBundle.arianaV1()
        val files = buildSet {
            add(bundle.modelJsonPath)
            add(bundle.mocPath)
            add(bundle.physicsPath)
            add(bundle.cdiPath)
            addAll(bundle.texturePaths)
            addAll(bundle.expressionPaths.values)
        }

        assertTrue(bundle.validate(files).isValid)
    }

    @Test
    fun missingTextureFails() {
        val bundle = Live2DModelBundle.arianaV1()
        val files = buildSet {
            add(bundle.modelJsonPath)
            add(bundle.mocPath)
            add(bundle.physicsPath)
            add(bundle.cdiPath)
            addAll(bundle.texturePaths.dropLast(1))
            addAll(bundle.expressionPaths.values)
        }

        assertFalse(bundle.validate(files).isValid)
    }

    @Test
    fun missingExpressionFails() {
        val bundle = Live2DModelBundle.arianaV1()
        val files = buildSet {
            add(bundle.modelJsonPath)
            add(bundle.mocPath)
            add(bundle.physicsPath)
            add(bundle.cdiPath)
            addAll(bundle.texturePaths)
            addAll(bundle.expressionPaths.filterKeys { it != "stern" }.values)
        }

        val result = bundle.validate(files)
        assertFalse(result.isValid)
        assertTrue("stern" in result.missingExpressionIds)
    }
}
