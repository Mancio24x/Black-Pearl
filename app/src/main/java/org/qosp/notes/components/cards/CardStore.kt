package org.qosp.notes.components.cards

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One loyalty card. [format] is a ZXing `BarcodeFormat` name (e.g. `EAN_13`, `QR_CODE`). */
data class LoyaltyCard(
    val id: String,
    val name: String,
    val code: String,
    val format: String,
    val createdAt: Long,
)

/**
 * File based store of loyalty cards (drawer entry "Cards"), one JSON file in the app's private storage.
 * Separate from the notes database, like the HTML library: no schema change, not part of note backups or sync.
 * All methods block: call them off the main thread.
 */
class CardStore(private val file: File) {
    private val lock = Any()

    fun list(): List<LoyaltyCard> = synchronized(lock) { read() }

    fun add(name: String, code: String, format: String, now: Long): LoyaltyCard = synchronized(lock) {
        val card = LoyaltyCard(UUID.randomUUID().toString(), name.trim().ifEmpty { code }, code, format, now)
        write(read() + card)
        card
    }

    fun rename(id: String, name: String) = synchronized(lock) {
        val clean = name.trim()
        if (clean.isNotEmpty()) write(read().map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun delete(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }

    private fun read(): List<LoyaltyCard> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LoyaltyCard(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    code = o.getString("code"),
                    format = o.getString("format"),
                    createdAt = o.optLong("createdAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(cards: List<LoyaltyCard>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        cards.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("code", it.code)
                    .put("format", it.format).put("createdAt", it.createdAt),
            )
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
