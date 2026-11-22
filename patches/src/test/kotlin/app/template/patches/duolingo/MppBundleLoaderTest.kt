package app.template.patches.duolingo

import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.loadPatchesFromJar
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        assertTrue(
            bundleFiles.isNotEmpty(),
            "Expected at least one built .mpp bundle in ${bundleDirectory.absolutePath}",
        )

        // The Morphe Gradle plugin can emit more than one .mpp archive into
        // the output directory. The loader accepts a set of bundles, so test
        // all generated archives instead of assuming there is exactly one.
        val loadedPatches = loadPatchesFromJar(bundleFiles.toSet())

        assertEquals(
            1,
            loadedPatches.size,
            "Morphe's JVM bundle loader must discover exactly one named patch from the generated bundles: ${bundleFiles.joinToString { it.name }}",
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
