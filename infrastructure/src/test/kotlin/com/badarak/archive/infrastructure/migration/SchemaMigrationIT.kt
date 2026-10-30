package com.badarak.archive.infrastructure.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import javax.sql.DataSource

class SchemaMigrationIT {

    private val url = "jdbc:h2:mem:migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS archive"

    @Test
    fun `the migration creates the schema with the migrator account alone, then stops`() {
        val context = SchemaMigration.run(
            "--ARCHIVE_DB_URL=$url",
            "--ARCHIVE_DB_MIGRATOR_USERNAME=sa",
            "--ARCHIVE_DB_MIGRATOR_PASSWORD=",
            "--logging.structured.format.console=",
        )
        context.use {
            assertFalse(it.getBeanNamesForType(DataSource::class.java).any { name -> name == "dataSource" })
        }

        DriverManager.getConnection(url, "sa", "").use { connection ->
            val tables = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = 'ARCHIVE' AND UPPER(TABLE_NAME) = 'ARCHIVED_DOCUMENT'",
            )
            tables.next()
            assertEquals(1, tables.getInt(1))
        }
    }
}
