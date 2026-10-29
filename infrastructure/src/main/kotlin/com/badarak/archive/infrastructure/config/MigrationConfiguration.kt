package com.badarak.archive.infrastructure.config

import org.springframework.boot.liquibase.autoconfigure.LiquibaseConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Runs the Liquibase migrations with the account of the migrator, owner of the schema, instead of the application's. */
@Configuration(proxyBeanMethods = false)
class MigrationConfiguration {

    @Bean
    fun liquibaseConnectionDetails(migration: MigrationSettings, dataSource: DataSourceSettings): LiquibaseConnectionDetails =
        MigratorConnectionDetails(migration, dataSource)
}

/**
 * The migrations connect to the database of the application with the migrator account. When that account is the
 * application's own, as with a single-account test database, they share the connection of the application instead.
 */
class MigratorConnectionDetails(private val migration: MigrationSettings, private val dataSource: DataSourceSettings) :
    LiquibaseConnectionDetails {

    private val ownAccount = !migration.user.isNullOrBlank() && migration.user != dataSource.username

    override fun getUsername(): String? = migration.user.takeIf { ownAccount }

    override fun getPassword(): String? = migration.password.takeIf { ownAccount }

    override fun getJdbcUrl(): String? = null
}
