/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.learning

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LearningSummaryStore(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("ai_learning_summary_store", Context.MODE_PRIVATE)

    val directoryLabel: String
        get() =
            selectedTreeUri()?.let { uri -> uri.lastPathSegment ?: uri.toString() }
                ?: LearningSummaryStoragePolicy.relativePath

    val hasExportDirectory: Boolean
        get() =
            selectedTreeUri() != null ||
                LearningSummaryStoragePolicy.canWritePublicDocuments(Build.VERSION.SDK_INT)

    fun selectedTreeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }

    fun persistSummaryDirectory(uri: Uri, flags: Int) {
        val persistableFlags =
            flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching { context.contentResolver.takePersistableUriPermission(uri, persistableFlags) }
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun saveSummary(
        video: LearningVideo,
        markdown: String,
        existingPath: String? = null,
    ): LearningSummaryDocument {
        val createdAtMs = System.currentTimeMillis()
        val fileName = LearningSummaryNaming.fileName(video.id)
        // Keep an atomic local copy before touching a potentially unavailable document provider.
        val localPath = savePrivateDraft(fileName, markdown)
        val savedPath =
            runCatching { overwriteExistingDocument(existingPath, markdown) }.getOrNull()
                ?: selectedTreeUri()?.let { treeUri ->
                    runCatching { saveToDocumentTree(treeUri, fileName, markdown) }.getOrNull()
                }
                ?: runCatching { saveToPublicDocuments(fileName, markdown) }.getOrNull()
                ?: localPath
        return LearningSummaryDocument(
            markdown = markdown,
            savedPath = savedPath,
            createdAtMs = createdAtMs,
        )
    }

    private fun saveToDocumentTree(treeUri: Uri, fileName: String, markdown: String): String? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val document =
            tree.findFile(fileName) ?: tree.createFile("text/markdown", fileName) ?: return null
        context.contentResolver.openOutputStream(document.uri, "wt")?.use { output ->
            output.write(markdown.toByteArray(Charsets.UTF_8))
        } ?: return null
        return document.uri.toString()
    }

    private fun overwriteExistingDocument(existingPath: String?, markdown: String): String? {
        val uri =
            existingPath?.takeIf { it.startsWith("content://") }?.let(Uri::parse) ?: return null
        val document =
            DocumentFile.fromSingleUri(context, uri)?.takeIf { it.canWrite() } ?: return null
        context.contentResolver.openOutputStream(document.uri, "wt")?.use { output ->
            output.write(markdown.toByteArray(Charsets.UTF_8))
        } ?: return null
        return document.uri.toString()
    }

    private fun saveToPublicDocuments(fileName: String, markdown: String): String? {
        if (!LearningSummaryStoragePolicy.canWritePublicDocuments(Build.VERSION.SDK_INT)) {
            return null
        }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    LearningSummaryStoragePolicy.relativePath,
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        return runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(markdown.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open the public learning summary file.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri.toString()
            }
            .getOrElse {
                context.contentResolver.delete(uri, null, null)
                null
            }
    }

    private fun savePrivateDraft(fileName: String, markdown: String): String {
        val directory = File(context.filesDir, "AIStudyNotesDrafts")
        directory.mkdirs()
        val file = File(directory, fileName)
        val temporary = File(directory, ".$fileName.tmp")
        temporary.writeText(markdown)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        return file.absolutePath
    }

    fun readSummary(savedPath: String?): String? {
        if (savedPath.isNullOrBlank()) return null
        return runCatching {
                if (savedPath.startsWith("content://")) {
                    context.contentResolver
                        .openInputStream(Uri.parse(savedPath))
                        ?.bufferedReader()
                        ?.use { it.readText() }
                } else {
                    File(savedPath).takeIf(File::isFile)?.readText()
                }
            }
            .getOrNull()
    }

    fun readNote(video: LearningVideo, existingPath: String?): String {
        readSummary(existingPath)?.let {
            return it
        }
        val local =
            File(context.filesDir, "AIStudyNotesDrafts/${LearningSummaryNaming.fileName(video.id)}")
        if (local.isFile) return local.readText()
        check(existingPath.isNullOrBlank()) {
            "The existing note cannot be read. Restore folder access before updating it."
        }
        return ""
    }

    private companion object {
        const val KEY_TREE_URI = "tree_uri"
    }
}

object LearningSummaryStoragePolicy {
    const val relativePath = "Documents/AI Spatial Learning"

    fun canWritePublicDocuments(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.Q
}
