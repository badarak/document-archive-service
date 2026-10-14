ALTER TABLE archive.archived_document ADD COLUMN tenant_id VARCHAR(100) NOT NULL;

DROP INDEX archive.idx_archived_document_source_business_reference;

CREATE INDEX idx_archived_document_tenant_source_business_reference
    ON archive.archived_document (tenant_id, source, business_reference);

ALTER TABLE archive.archived_document DROP CONSTRAINT ck_archived_document_size_bytes;

ALTER TABLE archive.archived_document ADD CONSTRAINT ck_archived_document_size_bytes CHECK (size_bytes > 0);
