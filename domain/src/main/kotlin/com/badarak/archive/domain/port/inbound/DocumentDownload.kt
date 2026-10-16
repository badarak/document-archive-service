package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.ArchivedDocument
import java.io.InputStream

class DocumentDownload(val document: ArchivedDocument, val content: InputStream)
