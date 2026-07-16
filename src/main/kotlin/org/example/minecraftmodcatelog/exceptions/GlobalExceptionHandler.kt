package org.example.minecraftmodcatelog.exceptions

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(
        ex: ApplicationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        // Expected application/business exceptions are logged at WARN level
        logger.warn("Application exception: [${ex.javaClass.simpleName}] ${ex.message}")

        val errorResponse = ErrorResponseDTO(
            status = ex.status.value(),
            error = ex.status.reasonPhrase,
            message = ex.message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, ex.status)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParams(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Missing parameter: ${ex.message}")
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
        logger.warn("Method argument type mismatch: ${ex.message}")
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
