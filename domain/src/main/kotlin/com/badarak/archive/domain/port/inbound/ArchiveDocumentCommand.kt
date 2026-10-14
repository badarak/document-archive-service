package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.DocumentMetadata

class ArchiveDocumentCommand(val caller: AuthenticatedCaller, val metadata: DocumentMetadata, val file: UploadedFile)
