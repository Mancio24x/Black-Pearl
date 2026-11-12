package org.qosp.notes.components.backup

import android.content.Context
import android.net.Uri
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.ui.attachments.getAttachmentFilename

interface ProgressHandler {
    fun onProgressChanged(current: Int, max: Int)
    fun onCompletion()
    fun onFailure(e: Throwable)
}

interface MigrationHandler {
    fun migrate(deserialized: String): String
}

class DefaultMigrationHandler : MigrationHandler {
    override fun migrate(deserialized: String): String {
        return deserialized
    }
}

sealed interface AttachmentHandler {
    fun handle(old: Attachment): Attachment?

    class IncludeFiles(private val context: Context) : AttachmentHandler {
        /***
         * Map of filenames to their according content URI.
         */
        val attachmentsMap = mutableMapOf<String, Uri>()
        private val count = mutableMapOf<String, Int>()

        override fun handle(old: Attachment): Attachment? {
            val uri = Uri.parse(old.path)
            val existingFile = attachmentsMap
                .filterValues { it == uri }
                .map { it.key }
                .firstOrNull()

            if (existingFile == null) {
                val originalName = getAttachmentFilename(context, uri) ?: return null

                // Never reuse a name already in the map (e.g. a real file called "1_a.jpg"), or one file would be lost
                val fileName = BackupRules.uniqueName(originalName, (count[originalName] ?: 0) + 1) {
                    attachmentsMap.containsKey(it)
                }
                if (fileName != originalName) count[originalName] = fileName.substringBefore('_').toInt()
                attachmentsMap[fileName] = uri

                return old.copy(fileName = fileName, path = "")
            }

            return old.copy(fileName = existingFile, path = "")
        }
    }

    object KeepInfoOnly : AttachmentHandler {
        override fun handle(old: Attachment) = old
    }

    object KeepNothing : AttachmentHandler {
        override fun handle(old: Attachment): Attachment? = null
    }
}
