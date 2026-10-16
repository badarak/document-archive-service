package com.badarak.archive.domain.model

/** Page of results asked for: pages are numbered from 0, and a size out of bounds is refused rather than adjusted. */
data class PageRequest(val page: Int = FIRST_PAGE, val size: Int = DEFAULT_SIZE) {

    init {
        if (page < FIRST_PAGE) throw InvalidSearchException("page", "must not be negative")
        if (size !in 1..MAX_SIZE) throw InvalidSearchException("size", "must be between 1 and $MAX_SIZE")
    }

    companion object {
        const val FIRST_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}
