package com.badarak.archive.domain.model

/** One page of the documents matching a search, with the number of matching documents over all pages. */
class DocumentPage(val documents: List<ArchivedDocument>, val request: PageRequest, val totalElements: Long) {

    val totalPages: Long = (totalElements + request.size - 1) / request.size
}
