package org.example.minecraftmodcatelog.exceptions

import org.springframework.http.HttpStatus

/**
 * Base exception class for all business and application-specific exceptions.
 */
abstract class ApplicationException(
    override val message: String,
    val status: HttpStatus,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when a requested resource (e.g. Mod, ModVersion) cannot be found.
 * Maps to HTTP 404 Not Found.
 */
class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.NOT_FOUND, cause)

/**
 * Thrown when client requests are malformed or fail validation.
 * Maps to HTTP 400 Bad Request.
 */
class InvalidRequestException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.BAD_REQUEST, cause)

/**
 * Thrown when an action cannot be performed due to the current state of a resource.
 * Maps to HTTP 409 Conflict.
 */
class InvalidStateException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.CONFLICT, cause)

/**
 * Thrown when external API integrations (e.g., Modrinth) fail or return errors.
 * Maps to HTTP 502 Bad Gateway.
 */
class ExternalServiceException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.BAD_GATEWAY, cause)
