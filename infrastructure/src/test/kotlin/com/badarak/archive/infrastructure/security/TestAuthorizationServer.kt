package com.badarak.archive.infrastructure.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

/**
 * Stands in for the authorization server in integration tests: signs access tokens with its private
 * key and publishes only the matching public key over HTTP. It lives as long as the test JVM, so
 * every test shares the same key set URI.
 *
 * The foreign key pair shares the identifier of the signing one, so a forged token is rejected on its
 * signature alone.
 */
object TestAuthorizationServer {
    const val ISSUER = "https://auth.test"
    const val AUDIENCE = "document-archive-service"
    const val TENANT = "acme"
    const val CLIENT = "billing-service"

    private val signingKeyPair: RSAKey = generateKeyPair()
    private val foreignKeyPair: RSAKey = generateKeyPair()

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        val keySet = JWKSet(signingKeyPair.toPublicJWK()).toString().toByteArray()
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
        notBefore: Instant? = null,
        scope: String? = null,
        signedByServer: Boolean = true,
        tenant: String? = TENANT,
        client: String? = CLIENT,
    ): String {
        val keyPair = if (signedByServer) signingKeyPair else foreignKeyPair
        val claims = claims(issuer, audience, expiresAt, notBefore, scope, tenant, client)
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyPair.keyID).build(), claims)
            .apply { sign(RSASSASigner(keyPair)) }
            .serialize()
    }

    /** A token without signature (alg none), otherwise valid. */
    fun unsignedToken(scope: String): String = PlainJWT(claims(scope = scope)).serialize()

    /**
     * A token signed with HMAC, keyed with the server's public key: it would pass a decoder that lets the token choose its
     * algorithm and verifies it with the published key.
     */
    fun hmacTokenKeyedWithPublicKey(scope: String): String {
        val secret = signingKeyPair.toRSAPublicKey().encoded
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).keyID(signingKeyPair.keyID).build(), claims(scope = scope))
            .apply { sign(MACSigner(secret)) }
            .serialize()
    }

    private fun claims(
        issuer: String = ISSUER,
        audience: List<String> = listOf(AUDIENCE),
        expiresAt: Instant = Instant.now().plusSeconds(300),
        notBefore: Instant? = null,
        scope: String? = null,
        tenant: String? = TENANT,
        client: String? = CLIENT,
    ): JWTClaimsSet = JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject("billing-service")
        .audience(audience)
        .issueTime(Date.from(expiresAt.minusSeconds(600)))
        .expirationTime(Date.from(expiresAt))
        .apply { notBefore?.let { notBeforeTime(Date.from(it)) } }
        .apply { scope?.let { claim("scope", it) } }
        .apply { tenant?.let { claim("tenant_id", it) } }
        .apply { client?.let { claim("azp", it) } }
        .build()

    private fun generateKeyPair(): RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
}
