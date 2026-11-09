package org.qosp.notes.components.importer

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Turns a picked file into [ImportEntry]s. Zip (Google Takeout, Simplenote, Obsidian vaults…), tar / .tgz
 * (Takeout) and Joplin .jex (a tar) are opened; anything else is a single entry.
 * Nothing is extracted to disk except a .tgz, which is un-gzipped once into [workDir].
 */
object ImportArchives {

    fun entriesOf(file: File, displayName: String, workDir: File): List<ImportEntry> {
        val lower = displayName.lowercase()
        return when {
            hasMagic(file, 0, byteArrayOf(0x50, 0x4B, 0x03, 0x04)) -> zipEntries(file)
            lower.endsWith(".tgz") || lower.endsWith(".tar.gz") || hasMagic(file, 0, byteArrayOf(0x1F, 0x8B.toByte())) -> {
                val tar = File(workDir, file.name + ".tar")
                GZIPInputStream(file.inputStream().buffered()).use { input -> tar.outputStream().use { input.copyTo(it) } }
                tarEntries(tar)
            }
            hasMagic(file, 257, "ustar".toByteArray()) -> tarEntries(file)
            else -> listOf(ImportEntry(displayName) { file.inputStream() })
        }
    }

    private fun hasMagic(file: File, offset: Long, magic: ByteArray): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < offset + magic.size) return false
            raf.seek(offset)
            val bytes = ByteArray(magic.size).also { raf.readFully(it) }
            bytes.contentEquals(magic)
        }
    }.getOrDefault(false)

    private fun zipEntries(file: File): List<ImportEntry> {
        // Zips made on Windows may store names in the old DOS encoding instead of UTF-8
        val charset = listOfNotNull(Charsets.UTF_8, runCatching { Charset.forName("IBM437") }.getOrNull())
            .firstOrNull { cs -> runCatching { ZipFile(file, cs).use { zip -> zip.entries().toList() } }.isSuccess }
            ?: throw IOException("Unreadable zip file")
        return ZipFile(file, charset).use { zip ->
            zip.entries().toList().filterNot { it.isDirectory }.map { entry ->
                val name = entry.name
                ImportEntry(name.trimStart('/'), entry.time.takeIf { it > 0 }?.let { Dates.fromNumber(it) }) {
                    val opened = ZipFile(file, charset)
                    object : FilterInputStream(opened.getInputStream(opened.getEntry(name))) {
                        override fun close() {
                            super.close()
                            opened.close()
                        }
                    }
                }
            }
        }
    }

    /** Minimal ustar / pax / GNU tar reader (Joplin uses node-tar, Takeout GNU tar). */
    fun tarEntries(file: File): List<ImportEntry> {
        val entries = mutableListOf<ImportEntry>()
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(512)
            var position = 0L
            var longName: String? = null
            while (position + 512 <= raf.length()) {
                raf.seek(position)
                raf.readFully(header)
                if (header.all { it == 0.toByte() }) break
                val size = parseSize(header)
                val type = header[156].toInt().toChar()
                val dataStart = position + 512
                var name = longName ?: buildName(header)
                longName = null
                when (type) {
                    'x' -> longName = paxPath(readString(raf, dataStart, size))
                    'L' -> longName = readString(raf, dataStart, size).trimEnd('\u0000')
                    '0', '\u0000', '7' -> {
                        name = name.removePrefix("./")
                        if (name.isNotEmpty()) {
                            val mtime = runCatching { cString(header, 136, 12).trim().toLong(8) }.getOrNull()
                            entries += ImportEntry(name, mtime?.let(Dates::fromNumber)) { limitedStream(file, dataStart, size) }
                        }
                    }
                }
                position = dataStart + (size + 511) / 512 * 512
            }
        }
        return entries
    }

    private fun buildName(header: ByteArray): String {
        val name = cString(header, 0, 100)
        val isUstar = String(header, 257, 5, Charsets.US_ASCII) == "ustar"
        val prefix = if (isUstar) cString(header, 345, 155) else ""
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun cString(bytes: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && bytes[end] != 0.toByte()) end++
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseSize(header: ByteArray): Long {
        if (header[124].toInt() and 0x80 != 0) { // base-256 for huge files
            var value = 0L
            for (i in 125 until 136) value = (value shl 8) or (header[i].toLong() and 0xFF)
            return value
        }
        return cString(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
    }

    private fun readString(raf: RandomAccessFile, offset: Long, size: Long): String {
        raf.seek(offset)
        val bytes = ByteArray(size.toInt()).also { raf.readFully(it) }
        return String(bytes, Charsets.UTF_8)
    }

    /** Pax records: "<length> key=value\n". */
    private fun paxPath(records: String): String? =
        records.lineSequence().map { it.substringAfter(' ') }.firstOrNull { it.startsWith("path=") }?.removePrefix("path=")

    private fun limitedStream(file: File, offset: Long, size: Long): InputStream {
        val input = file.inputStream()
        var toSkip = offset
        while (toSkip > 0) {
            val skipped = input.skip(toSkip)
            if (skipped <= 0) break
            toSkip -= skipped
        }
        return object : FilterInputStream(input) {
            private var left = size
            override fun read(): Int = if (left <= 0) -1 else super.read().also { if (it >= 0) left-- }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = super.read(b, off, minOf(len.toLong(), left).toInt())
                if (n > 0) left -= n
                return n
            }
            override fun available(): Int = minOf(super.available().toLong(), left).toInt()
        }
    }
}
