ALTER TABLE archive.archived_document DROP CONSTRAINT ck_archived_document_deleted_at;

ALTER TABLE archive.archived_document DROP CONSTRAINT ck_archived_document_status;

ALTER TABLE archive.archived_document DROP COLUMN deleted_at;
