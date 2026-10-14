package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.TenantId

/** Service calling the API, as identified by its access token: the tenant it acts for and its own name. */
data class AuthenticatedCaller(val tenantId: TenantId, val source: String)
