package com.example

import com.example.data.upstream.UpstreamManifest
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DualWorkbenchManifestUnitTest {

    @Test
    fun upstreamManifestAssetIncludesOriginalMseAndDataEditorX() {
        val assetFile = listOf(
            File("src/main/assets/upstream_manifest.json"),
            File("app/src/main/assets/upstream_manifest.json"),
        ).firstOrNull { it.exists() }
        assertNotNull("Expected upstream_manifest.json asset file", assetFile)

        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(UpstreamManifest::class.java)
        val manifest = adapter.fromJson(assetFile!!.readText())
        assertNotNull("Manifest should parse cleanly", manifest)

        val engineIds = manifest!!.engineSplits.map { it.id }.toSet()
        assertTrue("Expected mse_modern split", "mse_modern" in engineIds)
        assertTrue("Expected mse_legacy split", "mse_legacy" in engineIds)
        assertTrue("Expected dataeditorx_official split", "dataeditorx_official" in engineIds)

        val dexSplit = manifest.engineSplits.first { it.id == "dataeditorx_official" }
        assertEquals("DataEditorX", dexSplit.repo)
        assertEquals("dataeditorx", dexSplit.engineTarget)
    }
}
