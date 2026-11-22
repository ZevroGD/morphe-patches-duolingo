package app.template.patches.duolingo

import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.loadPatchesFromJar
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class MppBundleLoaderTest {
    @Test
    fun `built bundle is discovered from the bundle by Morphe patch loader`() {
        val bundleDirectory = File(
            requireNotNull(System.getProperty("mpp.bundle.dir")) {
                "Gradle must provide the absolute .mpp output directory"
            },
        )
        val bundleFiles = bundleDirectory
            .listFiles { file -> file.isFile && file.extension == "mpp" }
            .orEmpty()

        assertEquals(
            1,
            bundleFiles.size,
            "Expected exactly one built .mpp bundle in ${bundleDirectory.absolutePath}",
        )

        val loadedPatches = loadPatchesFromJar(bundleFiles.toSet())

        assertEquals(
            1,
            loadedPatches.size,
            "Morphe's JVM bundle loader must discover exactly one named patch from the bundle",
        )

        val patch = loadedPatches.single()
        assertEquals("Duolingo Linear Progress Bar", patch.name)
        assertNotNull(patch.description)
        assertIs<BytecodePatch>(patch)
        assertNotEquals(
            this::class.java.classLoader,
            patch.javaClass.classLoader,
            "Patch class must be loaded from the .mpp, not from the test classpath",
        )
    }
}
