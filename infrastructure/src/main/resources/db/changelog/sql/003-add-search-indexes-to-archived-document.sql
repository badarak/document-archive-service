CREATE INDEX idx_archived_document_tenant_created
    ON archive.archived_document (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_archived_document_tenant_type_created
    ON archive.archived_document (tenant_id, document_type, created_at DESC, id DESC);
