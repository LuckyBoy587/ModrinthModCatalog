package org.example.minecraftmodcatelog.exceptions

import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
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
 * General business logic exception.
 */
open class BusinessException(
    message: String,
    status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    cause: Throwable? = null
) : ApplicationException(message, status, cause)

/**
 * Thrown when validation fails on business rules or inputs.
 */
class ValidationException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.BAD_REQUEST, cause)

/**
 * Thrown when there is a state conflict (similar to InvalidStateException).
 */
class ConflictException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.CONFLICT, cause)

/**
 * Thrown when a bad request payload or parameter is received.
 */
class BadRequestException(
    message: String,
    cause: Throwable? = null
) : ApplicationException(message, HttpStatus.BAD_REQUEST, cause)

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

/**
 * Thrown when a dependency of a mod does not have a version for the requested Minecraft version and loader.
 * Maps to HTTP 400 Bad Request.
 */
class DependencyVersionNotFoundException(
    val missingDependencies: List<String>,
    val workingVersions: List<ModVersionWithoutDependenciesDTO>,
    cause: Throwable? = null
) : ApplicationException(
    "Dependency version not found for: ${missingDependencies.joinToString(", ")}",
    HttpStatus.BAD_REQUEST,
    cause
)

class UserAlreadyExistsException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.CONFLICT, cause)
class UserNotFoundException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.NOT_FOUND, cause)
class InvalidCredentialsException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.UNAUTHORIZED, cause)
class InvalidTokenException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.UNAUTHORIZED, cause)
class InvalidPasswordException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.BAD_REQUEST, cause)
class UnauthorizedException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.UNAUTHORIZED, cause)
class ForbiddenException(message: String, cause: Throwable? = null) : ApplicationException(message, HttpStatus.FORBIDDEN, cause)
