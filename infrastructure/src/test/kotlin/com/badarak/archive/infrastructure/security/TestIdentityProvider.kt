package com.badarak.archive.infrastructure.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

/**
 * Stands in for the identity provider in integration tests: publishes a key set over HTTP and
 * signs access tokens. It lives as long as the test JVM, so every test shares the same key set URI.
 */
object TestIdentityProvider {
    const val ISSUER = "https://idp.test"
    const val AUDIENCE = "document-archive-service"

    private val publishedKey: RSAKey = generateKey()
    private val unpublishedKey: RSAKey = generateKey()

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        val keySet = JWKSet(publishedKey.toPublicJWK()).toString().toByteArray()
        createContext("/jwks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, keySet.size.toLong())
            exchange.responseBody.use { it.write(keySet) }
        }
        start()
    }

    val jwksUri: String = "http://localhost:${server.address.port}/jwks"

    fun token(
        issuer: String = ISSUER,
        audience: List<String> = listOf(AUDIENCE),
        expiresAt: Instant = Instant.now().plusSeconds(300),
        scope: String? = null,
        signedWithPublishedKey: Boolean = true,
    ): String {
        val key = if (signedWithPublishedKey) publishedKey else unpublishedKey
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("billing-service")
            .audience(audience)
            .issueTime(Date.from(expiresAt.minusSeconds(600)))
            .expirationTime(Date.from(expiresAt))
            .apply { scope?.let { claim("scope", it) } }
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }
            .serialize()
    }

    // Both keys share the same identifier, so a forged token is rejected on its signature alone.
    private fun generateKey(): RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
}
