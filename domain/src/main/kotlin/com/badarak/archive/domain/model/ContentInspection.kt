package com.badarak.archive.domain.model

/** What the inspection of a content found, the content being expected as a file of a given format. */
sealed interface ContentInspection {
    /** The content is a file of the format. */
    data object Conforming : ContentInspection

    /** The content is not a file of the format; [detectedMediaType] names what it is, when it is recognised. */
    data class OtherFormat(val detectedMediaType: String?) : ContentInspection

    /** The content is of the format, but its structure is broken or holds what the format must not: [reason] says how. */
    data class Malformed(val reason: String) : ContentInspection
}
