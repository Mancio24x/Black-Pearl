package org.qosp.notes.data.sync.nextcloud

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudNote(
    val id: Long,
    val etag: String? = null,
    val content: String?,
    val title: String,
    // null = field left out of the request body (Json encodeDefaults=false), so an update keeps the server category
    val category: String? = null,
    val favorite: Boolean,
    val modified: Long, // seconds
    val readOnly: Boolean? = null,
    val remoteId: String = id.toString(),
)
