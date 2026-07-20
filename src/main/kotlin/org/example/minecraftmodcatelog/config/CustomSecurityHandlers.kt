package org.example.minecraftmodcatelog.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mapOf(
            "timestamp" to Instant.now().toString(),
            "status" to HttpServletResponse.SC_UNAUTHORIZED,
            "error" to HttpStatus.UNAUTHORIZED.reasonPhrase,
            "message" to (authException.message ?: "Unauthorized access"),
            "path" to request.requestURI
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mapOf(
            "timestamp" to Instant.now().toString(),
            "status" to HttpServletResponse.SC_FORBIDDEN,
            "error" to HttpStatus.FORBIDDEN.reasonPhrase,
            "message" to (accessDeniedException.message ?: "Access Denied"),
            "path" to request.requestURI
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}
