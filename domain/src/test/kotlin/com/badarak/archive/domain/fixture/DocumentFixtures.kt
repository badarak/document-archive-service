package com.badarak.archive.domain.fixture

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentLifecycle
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import java.time.Instant

val DOCUMENT_INSTANT: Instant = Instant.parse("2026-10-15T09:15:00Z")

/**
 * An archived invoice of the acme tenant, sent by the billing service, in any status: each test names only what matters to
 * it. The document was created and last updated at [at], and deleted then too when its status is DELETED.
 */
fun aDocument(
    status: DocumentStatus = DocumentStatus.AVAILABLE,
    id: DocumentId = DocumentId.generate(),
    tenant: String = "acme",
    source: String = "billing-service",
    metadata: DocumentMetadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
    file: StoredFile = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
    at: Instant = DOCUMENT_INSTANT,
): ArchivedDocument = ArchivedDocument(
    id = id,
    owner = DocumentOwner(TenantId(tenant), source),
    metadata = metadata,
    file = file,
    lifecycle = DocumentLifecycle(status, at, at, at.takeIf { status == DocumentStatus.DELETED }),
)
