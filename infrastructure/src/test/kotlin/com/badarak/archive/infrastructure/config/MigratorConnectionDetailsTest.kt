package com.badarak.archive.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MigratorConnectionDetailsTest {

    private val application = DataSourceSettings("jdbc:postgresql://db/archive", "archive_app", "app-password")

    @Test
    fun `the migrations connect with the migrator account, to the database of the application`() {
        val details = MigratorConnectionDetails(MigrationSettings(true, "archive_migrator", "migrator-password"), application)

        assertEquals("archive_migrator", details.username)
        assertEquals("migrator-password", details.password)
        assertNull(details.jdbcUrl)
    }

    @Test
    fun `the migrations share the connection of the application when they have the same account`() {
        val details = MigratorConnectionDetails(MigrationSettings(true, "archive_app", "app-password"), application)

        assertNull(details.username)
        assertNull(details.password)
    }

    @Test
    fun `the migrations share the connection of the application when they have no account, as when a separate job runs them`() {
        val details = MigratorConnectionDetails(MigrationSettings(false, "", ""), application)

        assertNull(details.username)
        assertNull(details.password)
    }
}
