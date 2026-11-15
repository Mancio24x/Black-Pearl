package org.qosp.notes.ui.cards

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qosp.notes.components.cards.CardCodes
import org.qosp.notes.components.cards.CardStore
import org.qosp.notes.components.cards.LoyaltyCard

class CardsViewModel(
    private val context: Context,
    private val store: CardStore,
) : ViewModel() {
    val cards = MutableStateFlow<List<LoyaltyCard>>(emptyList())

    /** Result of reading a picture: the code found, or null when there is none. */
    val pictureScans = MutableSharedFlow<CardCodes.Scan?>(extraBufferCapacity = 1)

    fun refresh() {
        viewModelScope.launch { cards.value = withContext(Dispatchers.IO) { store.list() } }
    }

    fun add(name: String, scan: CardCodes.Scan) = mutate { store.add(name, scan.code, scan.format, System.currentTimeMillis()) }

    fun rename(card: LoyaltyCard, name: String) = mutate { store.rename(card.id, name) }

    fun delete(card: LoyaltyCard) = mutate { store.delete(card.id) }

    fun scanPicture(uri: Uri) {
        viewModelScope.launch {
            pictureScans.emit(withContext(Dispatchers.IO) { runCatching { decodePicture(uri) }.getOrNull() })
        }
    }

    private fun decodePicture(uri: Uri): CardCodes.Scan? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        // Big photos are read at a reduced size: enough for a barcode, and safe for memory.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return CardCodes.decode(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh()
        }
    }

    private companion object {
        const val MAX_SIDE = 2000
    }
}
