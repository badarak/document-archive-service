package com.badarak.archive.domain

import java.util.UUID

/**
 * Key of a document's content in the object storage. [generate] draws it at random, so it never
 * derives from the document identifier, the tenant or anything the caller sent.
 */
@JvmInline
value class ObjectKey(val value: String) {

    init {
        if (value.isBlank()) throw InvalidDocumentException("objectKey", "must not be blank")
        if (value.length > MAX_LENGTH) throw InvalidDocumentException("objectKey", "must not exceed $MAX_LENGTH characters")
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 512
        const val PREFIX = "documents/"

        fun generate(): ObjectKey = ObjectKey("$PREFIX${UUID.randomUUID()}")
    }
}
