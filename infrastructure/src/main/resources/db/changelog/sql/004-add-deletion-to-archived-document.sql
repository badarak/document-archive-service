ALTER TABLE archive.archived_document ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE archive.archived_document ADD CONSTRAINT ck_archived_document_status
    CHECK (status IN ('PENDING', 'AVAILABLE', 'FAILED', 'DELETING', 'DELETED'));

ALTER TABLE archive.archived_document ADD CONSTRAINT ck_archived_document_deleted_at
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
