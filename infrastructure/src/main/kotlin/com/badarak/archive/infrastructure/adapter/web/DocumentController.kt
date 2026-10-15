package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.inbound.UploadedFile
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
class DocumentController(private val archiveDocument: ArchiveDocumentUseCase, private val callerResolver: JwtCallerResolver) :
    DocumentApi {

    override fun archive(file: MultipartFile, metadata: ArchiveMetadataRequest, jwt: Jwt): ResponseEntity<ArchivedDocumentResponse> {
        val caller = callerResolver.resolve(jwt)
        val documentMetadata = metadata.toDomain()
        val document = file.inputStream.use { content ->
            archiveDocument.archive(
                ArchiveDocumentCommand(
                    caller = caller,
                    metadata = documentMetadata,
                    file = UploadedFile(file.originalFilename.orEmpty(), file.contentType.orEmpty(), file.size, content),
                ),
            )
        }
        val location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(document.id).toUri()
        return ResponseEntity.created(location).body(ArchivedDocumentResponse.from(document))
    }
}
