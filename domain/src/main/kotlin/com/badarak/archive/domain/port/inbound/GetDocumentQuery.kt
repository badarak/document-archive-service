package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.DocumentId

class GetDocumentQuery(val caller: AuthenticatedCaller, val id: DocumentId)
