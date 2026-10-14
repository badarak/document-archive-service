package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.TenantId

data class AuthenticatedCaller(val tenantId: TenantId, val source: String)
