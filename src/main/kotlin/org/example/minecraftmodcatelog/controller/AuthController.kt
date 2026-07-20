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
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}
