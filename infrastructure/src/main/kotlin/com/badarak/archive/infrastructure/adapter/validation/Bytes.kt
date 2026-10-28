package com.badarak.archive.infrastructure.adapter.validation

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

internal fun ByteArray.hasAt(offset: Int, expected: String): Boolean = hasAt(offset, expected.toByteArray(Charsets.US_ASCII))

internal fun ByteArray.hasAt(offset: Int, expected: ByteArray): Boolean =
    offset >= 0 && size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }
