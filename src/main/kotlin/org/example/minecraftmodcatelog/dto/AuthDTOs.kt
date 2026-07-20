package org.example.minecraftmodcatelog.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\",.<>/?~`|\\\\/]).{8,}$",
        message = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character."
    )
    val password: String
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class LoginResponse(
    val token: String,
    val type: String = "Bearer"
)
