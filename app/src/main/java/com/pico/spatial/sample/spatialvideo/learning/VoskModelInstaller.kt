package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VoskModelLayout {
    private val requiredFiles =
        setOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "conf/model.conf",
            "graph/HCLr.fst",
        )

    fun isComplete(relativePaths: Set<String>): Boolean =
        requiredFiles.all { required ->
            relativePaths.any { path -> path.replace('\\', '/') == required }
        }

    fun isComplete(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val paths =
            directory
                .walkTopDown()
                .filter(File::isFile)
                .map { it.relativeTo(directory).path.replace('\\', '/') }
                .toSet()
        return isComplete(paths)
    }
}

class VoskModelInstaller(private val context: Context) {
    val modelDirectory: File
        get() = File(context.filesDir, MODEL_DIRECTORY_NAME)

    val isInstalled: Boolean
        get() = VoskModelLayout.isComplete(modelDirectory)

    suspend fun ensureInstalled(): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isInstalled) return@runCatching modelDirectory
                val stagingDirectory = File(context.filesDir, STAGING_DIRECTORY_NAME)
                stagingDirectory.deleteRecursively()
                check(stagingDirectory.mkdirs()) {
                    "Unable to create the Vosk model staging directory."
                }
                try {
                    extractBundledModel(stagingDirectory)
                    check(VoskModelLayout.isComplete(stagingDirectory)) {
                        "The bundled Vosk model is incomplete."
                    }
                    modelDirectory.deleteRecursively()
                    check(stagingDirectory.renameTo(modelDirectory)) {
                        "Unable to activate the bundled Vosk model."
                    }
                    modelDirectory
                } catch (error: Exception) {
                    stagingDirectory.deleteRecursively()
                    throw error
                }
            }
        }

    private fun extractBundledModel(stagingDirectory: File) {
        val canonicalRoot = stagingDirectory.canonicalFile
        ZipInputStream(context.assets.open(MODEL_ASSET_PATH).buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val relativePath = entry.name.removePrefix("$MODEL_ARCHIVE_ROOT/")
                if (relativePath.isNotBlank()) {
                    val output = File(stagingDirectory, relativePath).canonicalFile
                    check(
                        output.path == canonicalRoot.path ||
                            output.path.startsWith(canonicalRoot.path + File.separator)
                    ) {
                        "Invalid path in the bundled Vosk archive."
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { destination -> zip.copyTo(destination) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private companion object {
        const val MODEL_DIRECTORY_NAME = "vosk-model-small-cn-0.22"
        const val STAGING_DIRECTORY_NAME = ".vosk-model-installing"
        const val MODEL_ARCHIVE_ROOT = "vosk-model-small-cn-0.22"
        const val MODEL_ASSET_PATH = "models/vosk-model-small-cn-0.22.zip"
    }
}
