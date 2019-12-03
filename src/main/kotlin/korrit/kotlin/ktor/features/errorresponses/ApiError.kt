package korrit.kotlin.ktor.features.errorresponses

import java.time.OffsetDateTime

/**
 * Base API Error response.
 *
 * This definition is compatible with RFC 7807 "Problem Details for HTTP APIs".
 */
open class ApiError(
    val status: Int,
    val type: String,
    val title: String,
    val detail: String,
    val instance: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
