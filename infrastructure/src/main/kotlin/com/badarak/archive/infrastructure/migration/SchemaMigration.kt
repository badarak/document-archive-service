package com.badarak.archive.infrastructure.migration

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext

/**
 * Runs the Liquibase migrations with the migrator account, then stops: the job that migrates the schema before the
 * instances of a new version start with migrations off. Unlike the application, it needs neither the account of the
 * application, nor the storage, nor the authorization server. Not a component, so the application never loads it.
 */
@ImportAutoConfiguration(LiquibaseAutoConfiguration::class)
class SchemaMigration {

    companion object {
        private val MIGRATION_SETTINGS = arrayOf("--spring.liquibase.enabled=true", "--spring.liquibase.url=\${ARCHIVE_DB_URL}")

        fun run(vararg args: String): ConfigurableApplicationContext = SpringApplicationBuilder(SchemaMigration::class.java)
            .web(WebApplicationType.NONE)
            .run(*MIGRATION_SETTINGS, *args)
    }
}

fun main(args: Array<String>) {
    SchemaMigration.run(*args).close()
}
