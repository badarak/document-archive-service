CREATE TABLE archived_document (
    id                 UUID          NOT NULL,
    document_type      VARCHAR(50)   NOT NULL,
    source             VARCHAR(100)  NOT NULL,
    business_reference VARCHAR(255),
    description        VARCHAR(1000),
    tags               JSONB         NOT NULL,
    file_name          VARCHAR(255)  NOT NULL,
    content_type       VARCHAR(255)  NOT NULL,
    size_bytes         BIGINT        NOT NULL,
    object_key         VARCHAR(512)  NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_archived_document PRIMARY KEY (id),
    CONSTRAINT uk_archived_document_object_key UNIQUE (object_key),
    CONSTRAINT ck_archived_document_size_bytes CHECK (size_bytes >= 0)
);

CREATE INDEX idx_archived_document_source_business_reference
    ON archived_document (source, business_reference);
