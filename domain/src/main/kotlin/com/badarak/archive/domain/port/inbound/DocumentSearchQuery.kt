package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.PageRequest

class DocumentSearchQuery(val caller: AuthenticatedCaller, val criteria: DocumentSearchCriteria, val page: PageRequest)
