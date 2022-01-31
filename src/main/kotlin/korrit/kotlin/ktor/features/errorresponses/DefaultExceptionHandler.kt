package korrit.kotlin.ktor.features.errorresponses

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.BadRequestException
import io.ktor.features.ContentTransformationException
import io.ktor.features.NotFoundException
import io.ktor.features.UnsupportedMediaTypeException
import io.ktor.features.callId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.HttpStatusCode.Companion.allStatusCodes
import io.ktor.http.content.OutgoingContent.NoContent
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.TimeoutCancellationException
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.TimeoutException

/**
 * Base exception handler for HTTP API server.
 */
open class DefaultExceptionHandler(
    @PublishedApi internal val config: ErrorResponses.Configuration
) {

    private val log = LoggerFactory.getLogger(DefaultExceptionHandler::class.java)

    /**
     * Registers call pipeline exception type to HTTP status mapping.
     */
    inline fun <reified T : Throwable> register(status: HttpStatusCode) {
        config.exception<T> { handleKnown(it, status, this) }
    }

    /**
     * Registers send pipeline exception type to HTTP status mapping.
     */
    inline fun <reified T : Throwable> registerSend(status: HttpStatusCode) {
        config.sendException<T> { handleKnown(it, status, this) }
    }

    /**
     * Registers receive pipeline exception type to HTTP status mapping.
     */
    inline fun <reified T : Throwable> registerReceive(status: HttpStatusCode) {
        config.receiveException<T> { handleKnown(it, status, this) }
    }

    init {
        with(config) {
            exception<Throwable> { handleUnknown(it, this) }

            // Generic status exception
            exception<ResponseStatusException> { handleKnown(it, it.status, this) }

            allStatusCodes
                .filter { it.value >= BadRequest.value }
                .forEach {
                    status(it) { status ->
                        interceptErrorResponse(status, this)
                    }
                }

            // Internal ktor exceptions
            register<BadRequestException>(BadRequest)
            register<NotFoundException>(NotFound)
            register<UnsupportedMediaTypeException>(UnsupportedMediaType)
            registerReceive<ContentTransformationException>(BadRequest)

            // For some reason ktor by default maps them to 504, but no unhandled timeout should happen in the first place
            register<TimeoutException>(InternalServerError)
            register<TimeoutCancellationException>(InternalServerError)
        }
    }

    /**
     * Handler of errors for which status code was registered beforehand.
     */
    open suspend fun handleKnown(cause: Throwable, status: HttpStatusCode, ctx: PipelineContext<*, ApplicationCall>) = with(ctx) {
        when {
            status.value >= InternalServerError.value -> log.error(cause.message, cause)
            status.value >= BadRequest.value -> log.info(cause.message)
            else -> log.debug(cause.message, cause)
        }

        val error = constructError(
            status = status.value,
            type = cause.javaClass.name,
            title = status.description,
            path = call.request.path(),
            instance = call.callId!!,
            detail = cause.localizedMessage,
            cause = cause
        )

        call.respond(status, error)
    }

    /**
     * Handler of errors which were not registered beforehand.
     */
    open suspend fun handleUnknown(cause: Throwable, ctx: PipelineContext<Unit, ApplicationCall>) = with(ctx) {
        log.error("Unexpected error: ${cause.message}", cause)

        val status = InternalServerError
        val response = constructError(
            status = status.value,
            type = "UnexpectedError",
            title = status.description,
            path = call.request.path(),
            instance = call.callId!!,
            detail = "Internal server error, please, contact administrator",
            cause = cause
        )

        call.respond(status, response)
    }

    /**
     * Intercept error responses as some might not have been triggered by an exception.
     */
    open suspend fun interceptErrorResponse(status: HttpStatusCode, ctx: PipelineContext<*, ApplicationCall>) = with(ctx) {
        if (subject is NoContent) {
            val error = constructError(
                status = status.value,
                type = "Generic ${status.value}",
                title = status.description,
                path = call.request.path(),
                instance = call.callId!!,
                detail = status.toString()
            )

            call.respond(status, error)
        }
    }

    /**
     * Factory function to construct the actual error instance that is sent in response body.
     */
    @Suppress("LongParameterList") // factory function
    open fun constructError(
        status: Int,
        type: String,
        title: String,
        detail: String,
        instance: String,
        path: String,
        timestamp: OffsetDateTime = OffsetDateTime.now(),
        cause: Throwable? = null
    ): Any {
        return ApiError(status, type, title, detail, instance, path, timestamp)
    }
}
