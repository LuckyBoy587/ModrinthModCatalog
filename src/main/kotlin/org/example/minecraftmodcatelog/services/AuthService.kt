package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.config.JwtTokenProvider
import org.example.minecraftmodcatelog.dto.LoginRequest
import org.example.minecraftmodcatelog.dto.LoginResponse
import org.example.minecraftmodcatelog.dto.RegisterRequest
import org.example.minecraftmodcatelog.entities.User
import org.example.minecraftmodcatelog.entities.UserRole
import org.example.minecraftmodcatelog.exceptions.InvalidCredentialsException
import org.example.minecraftmodcatelog.exceptions.UserAlreadyExistsException
import org.example.minecraftmodcatelog.repositories.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun register(request: RegisterRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw UserAlreadyExistsException("A user with email '${request.email}' already exists.")
        }

        val hashedPassword = passwordEncoder.encode(request.password)!!
        val user = User(
            email = request.email,
            password = hashedPassword,
            role = UserRole.ROLE_USER
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException("Invalid email or password.")

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidCredentialsException("Invalid email or password.")
        }

        val token = jwtTokenProvider.createToken(user.email, user.role.name)
        return LoginResponse(token = token)
    }
}
