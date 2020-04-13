package korrit.kotlin.ktor.features.errorresponses

import java.time.OffsetDateTime

/**
 * Base API Error response.
 *
 * This definition is compatible with RFC 7807 "Problem Details for HTTP APIs".
 *
 * @property status The HTTP status code.
 * @property type Identifies the problem type.
 * @property title A short, human-readable summary of the problem type.
 * @property detail A human-readable explanation specific to this occurrence of the problem.
 * @property instance Unique identifier of this error.
 * @property path HTTP path that was requested.
 * @property timestamp Timestamp of the occurrence.
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
