package org.example.minecraftmodcatelog.config

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.example.minecraftmodcatelog.exceptions.InvalidTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${security.jwt.secret:dGhpcy1pcy1hLXZlcnktc2VjdXJlLWFuZC1sb25nLWVub3VnaC1zZWNyZXQta2V5LXdpdGgtYXQtbGVhc3QtMjU2LWJpdHMtZm9yLWhzMjU2LXVzYWdl}")
    private val secretString: String,

    @Value("\${security.jwt.expiration-ms:3600000}")
    private val validityInMilliseconds: Long
) {
    private val key: SecretKey by lazy {
        val decodedKey = Base64.getDecoder().decode(secretString)
        Keys.hmacShaKeyFor(decodedKey)
    }

    fun createToken(username: String, role: String): String {
        val claims = Jwts.claims()
            .subject(username)
            .add("role", role)
            .build()

        val now = Date()
        val validity = Date(now.time + validityInMilliseconds)

        return Jwts.builder()
            .claims(claims)
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun getUsername(token: String): String {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
        } catch (e: JwtException) {
            throw InvalidTokenException("Invalid or expired JWT token")
        } catch (e: IllegalArgumentException) {
            throw InvalidTokenException("Invalid or expired JWT token")
        }
    }

    fun getRole(token: String): String {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .get("role", String::class.java)
        } catch (e: Exception) {
            throw InvalidTokenException("Invalid or expired JWT token")
        }
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: JwtException) {
            throw InvalidTokenException("Expired or invalid JWT token")
        } catch (e: IllegalArgumentException) {
            throw InvalidTokenException("Expired or invalid JWT token")
        }
    }
}
