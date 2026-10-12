package com.badarak.archive

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DocumentArchiveApplication

fun main(args: Array<String>) {
    runApplication<DocumentArchiveApplication>(*args)
}
