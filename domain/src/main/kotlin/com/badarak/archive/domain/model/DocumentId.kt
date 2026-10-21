package com.badarak.archive.domain.model

import java.util.UUID

@JvmInline
value class DocumentId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun generate(): DocumentId = DocumentId(UUID.randomUUID())
    }
}
