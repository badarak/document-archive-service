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

    companion object {
        const val PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    }
}
