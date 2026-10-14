package com.badarak.archive.domain.model

@JvmInline
value class TenantId(val value: String) {

    init {
        if (value.isBlank() || value.length > MAX_LENGTH) throw InvalidTenantIdException(value)
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100
    }
}
