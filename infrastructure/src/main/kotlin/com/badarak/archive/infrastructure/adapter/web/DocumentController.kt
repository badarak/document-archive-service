package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.inbound.DeleteDocumentUseCase
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.inbound.DocumentSearchQuery
import com.badarak.archive.domain.port.inbound.DownloadDocumentUseCase
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.inbound.SearchDocumentsUseCase
import com.badarak.archive.domain.port.inbound.UploadedFile
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Tag(name = "Documents", description = "Archiving of documents sent by other services")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val archiveDocument: ArchiveDocumentUseCase,
    private val getDocument: GetDocumentUseCase,
    private val downloadDocument: DownloadDocumentUseCase,
    private val searchDocuments: SearchDocumentsUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val callerResolver: JwtCallerResolver,
) : DocumentApi {

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

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun search(
        @RequestParam documentType: String?,
        @RequestParam source: String?,
        @RequestParam businessReference: String?,
        @RequestParam createdFrom: Instant?,
        @RequestParam createdTo: Instant?,
        @RequestParam page: Int?,
        @RequestParam size: Int?,
        @AuthenticationPrincipal jwt: Jwt,
    ): DocumentPageResponse {
        val caller = callerResolver.resolve(jwt)
        val criteria = DocumentSearchCriteria(documentType?.let(::DocumentType), source, businessReference, createdFrom, createdTo)
        val request = PageRequest(page ?: PageRequest.FIRST_PAGE, size ?: PageRequest.DEFAULT_SIZE)
        return DocumentPageResponse.from(searchDocuments.search(DocumentSearchQuery(caller, criteria, request)))
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun get(@PathVariable id: UUID, @AuthenticationPrincipal jwt: Jwt): ArchivedDocumentResponse =
        ArchivedDocumentResponse.from(getDocument.get(DocumentQuery(callerResolver.resolve(jwt), DocumentId(id))))

    @GetMapping("/{id}/content")
    override fun download(@PathVariable id: UUID, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Resource> {
        val download = downloadDocument.download(DocumentQuery(callerResolver.resolve(jwt), DocumentId(id)))
        val file = download.document.file
        val disposition = ContentDisposition.attachment().filename(file.fileName, StandardCharsets.UTF_8).build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.sizeBytes)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(InputStreamResource(download.content))
    }

    @DeleteMapping("/{id}")
    override fun delete(@PathVariable id: UUID, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        deleteDocument.delete(DocumentQuery(callerResolver.resolve(jwt), DocumentId(id)))
        return ResponseEntity.noContent().build()
    }
}
