package org.qosp.notes.components.backup

/** Pure rules used by backup and restore (Black Pearl). Kept free of Android types so they run in JVM tests. */
object BackupRules {
    const val BACKUP_JSON = "backup.json"

    /**
     * Returns the file name to restore for the zip entry [entryName], or null when the entry must be skipped.
     * Only plain files directly inside [mediaFolder] are accepted: no sub-folders, no absolute paths,
     * no `.`/`..`, no backslashes (path traversal), no Mac junk (`._x`).
     */
    fun mediaFileName(entryName: String, isDirectory: Boolean, mediaFolder: String): String? {
        if (isDirectory) return null
        val prefix = "$mediaFolder/"
        if (!entryName.startsWith(prefix)) return null
        val name = entryName.removePrefix(prefix)
        if (name.isEmpty() || name == "." || name == ".." || name.startsWith("._")) return null
        if ('/' in name || '\\' in name || '\u0000' in name) return null
        return name
    }

    /**
     * Returns [name] if [isTaken] is false for it, otherwise the first free `<n>_<name>` with n >= [firstIndex].
     * Never returns a taken name, so it can't overwrite an existing entry.
     */
    fun uniqueName(name: String, firstIndex: Int = 1, isTaken: (String) -> Boolean): String {
        if (!isTaken(name)) return name
        var index = firstIndex.coerceAtLeast(1)
        while (isTaken("${index}_$name")) index++
        return "${index}_$name"
    }

    /**
     * Rewrites inline links to restored media in [content]. [links] maps the URI-encoded name the file had in the
     * backup to the full URI of the restored file. Any provider authority is matched (backups from another app id),
     * `media_files` is the FileProvider path name of the media folder (res/xml/file_paths.xml).
     */
    fun remapMediaLinks(content: String, links: Map<String, String>): String {
        if (links.isEmpty() || "/media_files/" !in content) return content
        // `(`, `)` and `'` are left as they are by Uri.encode, so `photo (1).jpg` is `photo%20(1).jpg` in a link
        val pattern = Regex("""content://[^/\s()<>\[\]"']+/media_files/([A-Za-z0-9._~%!$&'()*+,;=@-]+)""")
        return pattern.replace(content) { match ->
            // the match may also hold the `)` that closes a Markdown link: drop trailing `)` until a name is known
            var name = match.groupValues[1]
            var tail = ""
            while (name !in links && name.endsWith(')')) {
                name = name.dropLast(1)
                tail = ")$tail"
            }
            links[name]?.let { it + tail } ?: match.value
        }
    }
}
