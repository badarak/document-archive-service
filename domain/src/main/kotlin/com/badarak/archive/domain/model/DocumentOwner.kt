package com.badarak.archive.domain.model

/** Who a document is archived for: the tenant, and the service that sent it. */
data class DocumentOwner(val tenantId: TenantId, val source: String) {

    init {
        requireText("source", source, SOURCE_MAX_LENGTH)
    }

    companion object {
        const val SOURCE_MAX_LENGTH = 100
    }
}
