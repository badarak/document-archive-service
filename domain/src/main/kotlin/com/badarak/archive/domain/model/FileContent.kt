package com.badarak.archive.domain.model

import java.io.InputStream

/**
 * Content of a file that can be read several times, as its validation and its upload both need: each call to [open]
 * returns a new stream from the first byte, that the caller closes. It is backed by a file, never held in memory.
 */
fun interface FileContent {
    fun open(): InputStream
}
