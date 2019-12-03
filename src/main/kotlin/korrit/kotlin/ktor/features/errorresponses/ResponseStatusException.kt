package korrit.kotlin.ktor.features.errorresponses

import io.ktor.http.HttpStatusCode

/**
 * Generic status exception. Class derivatives do not have to be explicitly registered in exception handler.
 *
 * This class is abstract as using such generic exception would be bad practice.
 */
abstract class ResponseStatusException(
    val status: HttpStatusCode,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
