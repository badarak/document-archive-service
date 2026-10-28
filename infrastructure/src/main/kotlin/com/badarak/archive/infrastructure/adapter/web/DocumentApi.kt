package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.ParameterObject
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
        description = "Missing part, malformed metadata, invalid field, empty file, or file name holding a path or hidden characters",
    )
    @ApiResponse(responseCode = "403", description = "Token without the documents:write scope, or without tenant or source claim")
    @ApiResponse(responseCode = "413", description = "File larger than 50 MiB")
    @ApiResponse(responseCode = "415", description = "Format not allowed, or declared type or content not matching the extension")
    @ApiResponse(responseCode = "503", description = "Object storage or document store unavailable")
    fun archive(
        @Parameter(description = "The file to archive, with its original name and content type")
        file: MultipartFile,
        @Parameter(description = "Business metadata of the document, as JSON")
        metadata: ArchiveMetadataRequest,
        @Parameter(hidden = true)
        caller: AuthenticatedCaller,
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
    )
    @ApiResponse(responseCode = "403", description = "Token without the documents:read scope, or without tenant or source claim")
    @ApiResponse(responseCode = "503", description = "Document store unavailable")
    fun search(
        @ParameterObject
        request: SearchDocumentsRequest,
        @Parameter(hidden = true)
        caller: AuthenticatedCaller,
    ): DocumentPageResponse

    @Operation(
        summary = "Read the metadata of a document",
        description = "Returns an AVAILABLE document of the caller's tenant, without its content. The access token needs the " +
            "documents:read scope. A document that does not exist, belongs to another tenant or is not available is answered " +
            "with the same 404.",
    )
    @ApiResponse(responseCode = "200", description = "Metadata of the document")
    @ApiResponse(responseCode = "400", description = "Identifier that is not a UUID")
    @ApiResponse(responseCode = "403", description = "Token without the documents:read scope, or without tenant or source claim")
    @ApiResponse(responseCode = "404", description = "No available document with this identifier for the caller's tenant")
    @ApiResponse(responseCode = "503", description = "Document store unavailable")
    fun get(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        caller: AuthenticatedCaller,
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
    @ApiResponse(responseCode = "400", description = "Identifier that is not a UUID")
    @ApiResponse(responseCode = "403", description = "Token without the documents:read scope, or without tenant or source claim")
    @ApiResponse(responseCode = "404", description = "No available document with this identifier for the caller's tenant")
    @ApiResponse(responseCode = "500", description = "The document's content is missing from the storage")
    @ApiResponse(responseCode = "503", description = "Object storage or document store unavailable")
    fun download(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        caller: AuthenticatedCaller,
    ): ResponseEntity<Resource>

    @Operation(
        summary = "Delete a document",
        description = "Removes the content of an AVAILABLE document of the caller's tenant from the storage and records it as " +
            "deleted, keeping its metadata. The access token needs the documents:delete scope. The document can no longer be " +
            "read as soon as the deletion starts. Deleting a deleted document succeeds again, and replaying a deletion " +
            "interrupted by a storage failure finishes it.",
    )
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "400", description = "Identifier that is not a UUID")
    @ApiResponse(responseCode = "403", description = "Token without the documents:delete scope, or without tenant or source claim")
    @ApiResponse(responseCode = "404", description = "No available or deleted document with this identifier for the caller's tenant")
    @ApiResponse(
        responseCode = "503",
        description = "Object storage or document store unavailable; the document stays unreadable and the deletion can be replayed",
    )
    fun delete(
        @Parameter(description = "Identifier of the document, as returned when it was archived")
        id: UUID,
        @Parameter(hidden = true)
        caller: AuthenticatedCaller,
    ): ResponseEntity<Void>

    companion object {
        const val PATH = "/api/v1/documents"
    }
}
