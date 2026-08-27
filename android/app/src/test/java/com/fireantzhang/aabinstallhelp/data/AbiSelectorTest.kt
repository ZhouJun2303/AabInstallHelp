package com.fireantzhang.aabinstallhelp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AbiSelectorTest {
    @Test
    fun pickPreferred_usesFirstDeviceAbiPresentInAab() {
        val chosen = AbiSelector.pickPreferred(
            deviceAbis = listOf("x86_64", "arm64-v8a"),
            aabAbis = setOf("arm64-v8a")
        )
        assertEquals("arm64-v8a", chosen)
    }

    @Test
    fun pickPreferred_prefersPrimaryWhenBothPresent() {
        val chosen = AbiSelector.pickPreferred(
            deviceAbis = listOf("x86_64", "arm64-v8a"),
            aabAbis = setOf("x86_64", "arm64-v8a")
        )
        assertEquals("x86_64", chosen)
    }

    @Test
    fun pickPreferred_fallsBackToPrimaryDeviceAbiWhenAabHasNoNativeLibs() {
        val chosen = AbiSelector.pickPreferred(
            deviceAbis = listOf("x86_64", "x86", "arm64-v8a"),
            aabAbis = emptySet()
        )
        assertEquals("x86_64", chosen)
    }

    @Test
    fun detectAbis_readsNestedModuleZip() {
        val aab = File.createTempFile("nested-abi", ".aab")
        try {
            val module = zipBytes(
                "lib/arm64-v8a/libfoo.so" to byteArrayOf(1),
                "lib/armeabi-v7a/libfoo.so" to byteArrayOf(1)
            )
            writeZip(aab, "base.zip" to module)
            val abis = AbiSelector.detectAbis(aab)
            assertEquals(setOf("arm64-v8a", "armeabi-v7a"), abis)
        } finally {
            aab.delete()
        }
    }

    @Test
    fun detectAbis_readsFlatModulePaths() {
        val aab = File.createTempFile("flat-abi", ".aab")
        try {
            writeZip(
                aab,
                "base/lib/x86_64/libfoo.so" to byteArrayOf(1),
                "BundleConfig.pb" to byteArrayOf(0)
            )
            val abis = AbiSelector.detectAbis(aab)
            assertEquals(setOf("x86_64"), abis)
        } finally {
            aab.delete()
        }
    }

    @Test
    fun collectAbi_ignoresNonNativeLibPaths() {
        val found = linkedSetOf<String>()
        AbiSelector.collectAbi("assets/lib/notes.txt", found)
        AbiSelector.collectAbi("base/manifest/AndroidManifest.xml", found)
        assertTrue(found.isEmpty())
    }

    private fun writeZip(dest: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(dest.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
}
