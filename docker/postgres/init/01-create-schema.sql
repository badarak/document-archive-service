-- Local provisioning of the application schema. Runs once, when the data volume is first initialised.
-- Tables are created by Liquibase inside this schema, not here.
CREATE SCHEMA IF NOT EXISTS archive;
