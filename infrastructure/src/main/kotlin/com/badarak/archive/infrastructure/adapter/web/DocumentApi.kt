package com.badarak.archive.infrastructure.adapter.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@Tag(name = "Documents", description = "Archiving of documents sent by other services")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_JWT)
@RequestMapping("/api/v1/documents")
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
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun archive(
        @Parameter(description = "The file to archive, with its original name and content type")
        @RequestPart("file")
        file: MultipartFile,
        @Parameter(description = "Business metadata of the document, as JSON")
        @RequestPart("metadata")
        metadata: ArchiveMetadataRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
    ): ResponseEntity<ArchivedDocumentResponse>

    companion object {
        const val PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    }
}
