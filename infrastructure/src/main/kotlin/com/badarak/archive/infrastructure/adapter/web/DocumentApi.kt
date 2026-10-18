package com.badarak.archive.infrastructure.adapter.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

interface DocumentApi {

    @Operation(
        summary = "Archive a document",
        description = "Stores the file in the object storage and its metadata in the document store. " +
            "The tenant and the source are taken from the access token, which needs the documents:write scope.",
        requestBody = OpenApiRequestBody(
            content = [
                Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = [Encoding(name = "metadata", contentType = MediaType.APPLICATION_JSON_VALUE)],
                ),
            ],
        ),
    )
    @ApiResponse(responseCode = "201", description = "Document archived; Location points to the document")
    @ApiResponse(
        responseCode = "400",
        description = "Missing part, malformed metadata, invalid field or empty file",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Missing, invalid or expired access token",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Token without the documents:write scope, or without tenant or source claim",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "413",
        description = "File larger than 50 MiB",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "415",
        description = "Format not allowed, or declared type or content not matching the extension",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Object storage or document store unavailable",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun archive(
        @Parameter(description = "The file to archive, with its original name and content type")
        file: MultipartFile,
        @Parameter(description = "Business metadata of the document, as JSON")
        metadata: ArchiveMetadataRequest,
        @Parameter(hidden = true)
        jwt: Jwt,
    ): ResponseEntity<ArchivedDocumentResponse>

    @Operation(
        summary = "Search the documents",
        description = "Returns a page of the AVAILABLE documents of the caller's tenant matching every given filter, newest " +
            "first, then by descending identifier. The access token needs the documents:read scope. A page beyond the last " +
            "one is empty.",
    )
    @ApiResponse(responseCode = "200", description = "The requested page of matching documents")
    @ApiResponse(
        responseCode = "400",
        description = "Malformed filter, period ending before it starts, negative page, or size out of 1 to 100",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Missing, invalid or expired access token",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Token without the documents:read scope, or without tenant or source claim",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Document store unavailable",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun search(
        @Parameter(description = "Exact document type, such as INVOICE")
        documentType: String?,
        @Parameter(description = "Exact source, the service that archived the document")
        source: String?,
        @Parameter(description = "Exact business reference")
        businessReference: String?,
        @Parameter(description = "Earliest creation instant, inclusive, in ISO-8601 with an offset (2026-10-15T14:30:00Z)")
        createdFrom: Instant?,
        @Parameter(description = "Latest creation instant, inclusive, in ISO-8601 with an offset (2026-10-15T14:30:00Z)")
        createdTo: Instant?,
        @Parameter(description = "Number of the page, from 0", schema = Schema(defaultValue = "0", minimum = "0"))
        page: Int?,
        @Parameter(description = "Number of documents per page", schema = Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        size: Int?,
        @Parameter(hidden = true)
        jwt: Jwt,
    ): DocumentPageResponse

    @Operation(
        summary = "Read the metadata of a document",
        description = "Returns an AVAILABLE document of the caller's tenant, without its content. The access token needs the " +
            "documents:read scope. A document that does not exist, belongs to another tenant or is not available is answered " +
            "with the same 404.",
    )
    @ApiResponse(responseCode = "200", description = "Metadata of the document")
    @ApiResponse(
        responseCode = "400",
        description = "Identifier that is not a UUID",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Missing, invalid or expired access token",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Token without the documents:read scope, or without tenant or source claim",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No available document with this identifier for the caller's tenant",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Document store unavailable",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun get(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        jwt: Jwt,
    ): ArchivedDocumentResponse

    @Operation(
        summary = "Download the content of a document",
        description = "Streams the original file of an AVAILABLE document of the caller's tenant, as an attachment, with its " +
            "content type, size and file name. The access token needs the documents:read scope.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "The file, byte for byte",
        content = [Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = Schema(type = "string", format = "binary"))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Identifier that is not a UUID",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Missing, invalid or expired access token",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Token without the documents:read scope, or without tenant or source claim",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No available document with this identifier for the caller's tenant",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "500",
        description = "The document's content is missing from the storage",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Object storage or document store unavailable",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun download(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        jwt: Jwt,
    ): ResponseEntity<Resource>

    @Operation(
        summary = "Delete a document",
        description = "Removes the content of an AVAILABLE document of the caller's tenant from the storage and records it as " +
            "deleted, keeping its metadata. The access token needs the documents:delete scope. The document can no longer be " +
            "read as soon as the deletion starts. Deleting a deleted document succeeds again, and replaying a deletion " +
            "interrupted by a storage failure finishes it.",
    )
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(
        responseCode = "400",
        description = "Identifier that is not a UUID",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Missing, invalid or expired access token",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Token without the documents:delete scope, or without tenant or source claim",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No available or deleted document with this identifier for the caller's tenant",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Object storage or document store unavailable; the document stays unreadable and the deletion can be replayed",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun delete(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        jwt: Jwt,
    ): ResponseEntity<Void>

    companion object {
        const val PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    }
}
