package com.badarak.archive.domain

import java.util.UUID

@JvmInline
value class DocumentId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun generate(): DocumentId = DocumentId(UUID.randomUUID())

        fun parse(raw: String): DocumentId =
            try {
                DocumentId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Identifiant de document invalide : $raw", e)
            }
    }
}
