package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.inbound.UploadedFile
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@Tag(name = "Documents", description = "Archiving of documents sent by other services")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(private val archiveDocument: ArchiveDocumentUseCase, private val callerResolver: JwtCallerResolver) :
    DocumentApi {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun archive(
        @RequestPart("file") file: MultipartFile,
        @RequestPart("metadata") metadata: ArchiveMetadataRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ArchivedDocumentResponse> {
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
