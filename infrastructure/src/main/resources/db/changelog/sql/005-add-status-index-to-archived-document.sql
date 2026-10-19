CREATE INDEX idx_archived_document_status_updated
    ON archive.archived_document (status, updated_at);
