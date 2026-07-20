package org.example.minecraftmodcatelog.controller

import jakarta.validation.Valid
import org.example.minecraftmodcatelog.dto.LoginRequest
import org.example.minecraftmodcatelog.dto.LoginResponse
import org.example.minecraftmodcatelog.dto.RegisterRequest
import org.example.minecraftmodcatelog.services.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie

@RestController
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<Map<String, String>> {
        authService.register(request)
        return ResponseEntity.ok(mapOf("message" to "User registered successfully"))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        val loginResponse = authService.login(request)
        val cookie = ResponseCookie.from("mmc_auth_token", loginResponse.token)
            .httpOnly(true)
            .secure(true)
            .sameSite("None")
            .path("/")
            .maxAge(86400)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return ResponseEntity.ok(mapOf(
            "email" to request.email,
            "message" to "Login successful"
        ))
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        val cookie = ResponseCookie.from("mmc_auth_token", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("None")
            .path("/")
            .maxAge(0)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }
}
