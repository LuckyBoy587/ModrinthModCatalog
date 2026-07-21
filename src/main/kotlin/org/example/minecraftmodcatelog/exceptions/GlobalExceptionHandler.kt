package org.example.minecraftmodcatelog.exceptions

import jakarta.servlet.http.HttpServletRequest
import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(DependencyVersionNotFoundException::class)
    fun handleDependencyVersionNotFoundException(
        ex: DependencyVersionNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<DependencyErrorResponseDTO> {
        logger.warn("Dependency version not found exception: ${ex.message}", ex)

        val errorResponse = DependencyErrorResponseDTO(
            status = ex.status.value(),
            error = ex.status.reasonPhrase,
            message = ex.message,
            path = request.requestURI,
            missingDependencies = ex.missingDependencies,
            workingVersions = ex.workingVersions
        )
        return ResponseEntity(errorResponse, ex.status)
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(
        ex: ApplicationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        // Expected application/business exceptions are logged at WARN level with stack trace (but not error log)
        logger.warn("Application exception: [${ex.javaClass.simpleName}] ${ex.message}", ex)

        val errorResponse = ErrorResponseDTO(
            status = ex.status.value(),
            error = ex.status.reasonPhrase,
            message = ex.message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, ex.status)
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: org.springframework.security.access.AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Access denied exception: ${ex.message}", ex)
        val status = HttpStatus.FORBIDDEN
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message ?: "Access Denied",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException::class)
    fun handleAuthenticationException(
        ex: org.springframework.security.core.AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Authentication exception: ${ex.message}", ex)
        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message ?: "Full authentication is required to access this resource",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: jakarta.validation.ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Constraint violation: ${ex.message}", ex)
        val status = HttpStatus.BAD_REQUEST
        val message = ex.constraintViolations.joinToString(", ") { "${it.propertyPath}: ${it.message}" }
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Handler method validation failed: ${ex.message}", ex)
        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Bind exception: ${ex.message}", ex)
        val status = HttpStatus.BAD_REQUEST
        val errorMessage = ex.bindingResult.fieldErrors.joinToString(", ") { error ->
            "${error.field}: ${error.defaultMessage}"
        }
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = errorMessage,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParams(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Missing parameter: ${ex.message}", ex)
        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = "Required parameter '${ex.parameterName}' is missing.",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Method argument type mismatch: ${ex.message}", ex)
        val status = HttpStatus.BAD_REQUEST

        val rootCauseMessage = ex.mostSpecificCause.message ?: ex.message
        val message = "Parameter '${ex.name}' has invalid value: $rootCauseMessage"

        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        val status = HttpStatus.NOT_FOUND
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = "Path not found: ${request.requestURI}",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: org.springframework.web.bind.MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        val errorMessage = ex.bindingResult.fieldErrors.joinToString(", ") { error ->
            "${error.field}: ${error.defaultMessage}"
        }
        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = errorMessage,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(org.hibernate.LazyInitializationException::class)
    fun handleLazyInitializationException(
        ex: org.hibernate.LazyInitializationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.error("Lazy initialization exception occurred while accessing entity relationships: ${ex.message}", ex)
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = "An internal data access error occurred due to uninitialized relationships.",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        // Unexpected exceptions are logged at ERROR level with a full stack trace
        logger.error("Unexpected exception occurred while processing request to path: ${request.requestURI}", ex)

        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = "An unexpected error occurred. Please try again later.",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }
}

/**
 * Consistent error response JSON payload returned to API clients.
 */
data class ErrorResponseDTO(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String?,
    val path: String
)

/**
 * Error response JSON payload returned when dependencies are missing version details.
 */
data class DependencyErrorResponseDTO(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
    val missingDependencies: List<String>,
    val workingVersions: List<ModVersionWithoutDependenciesDTO>
)
