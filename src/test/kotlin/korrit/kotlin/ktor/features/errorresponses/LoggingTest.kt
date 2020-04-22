package korrit.kotlin.ktor.features.errorresponses

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import koriit.kotlin.slf4j.mdc.correlation.correlateThread
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class LoggingTest {

    init {
        correlateThread()
    }

    @Test
    fun `Status should be as in response if no exception`() = testServer().run {
        start()
        with(handleRequest(Get, "/no-content")) {
            assertEquals(HttpStatusCode.NoContent, response.status())
        }
        stop(0, 0)
    }

    @Test
    fun `Status should be 500 for unmapped exception`() = testServer().run {
        start()
        with(handleRequest(Get, "/unmapped-error")) {
            assertApiError(HttpStatusCode.InternalServerError)
        }
        stop(0, 0)
    }

    @Test
    fun `Status should as globally mapped for exception`() = testServer().run {
        start()
        with(handleRequest(Get, "/global-error")) {
            assertApiError(HttpStatusCode.UnprocessableEntity)
        }
        stop(0, 0)
    }

    @Test
    fun `Status should be as mapped for exception in receive`() = testServer().run {
        start()
        with(handleRequest(Post, "/receive-error") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }) {
            assertApiError(HttpStatusCode.BadRequest)
        }
        stop(0, 0)
    }

    @Test
    fun `Status should be as mapped for exception in send`() = testServer().run {
        start()
        with(handleRequest(Get, "/send-error")) {
            assertApiError(HttpStatusCode.Conflict)
        }
        stop(0, 0)
    }

    @Test
    fun `Error response should contain details`() = testServer().run {
        start()
        with(handleRequest(Post, "/receive-error") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }) {
            assertApiError(HttpStatusCode.BadRequest)
            val error: ApiError = jackson().readValue(response.content!!)
            assertEquals(400, error.status)
            assertEquals("Bad Request", error.title)
            assertEquals("com.fasterxml.jackson.databind.exc.MismatchedInputException", error.type)
            assertEquals("/receive-error", error.path)
            assertEquals("No content to map due to end-of-input\n at [Source: (InputStreamReader); line: 1, column: 0]", error.detail)
        }
        stop(0, 0)
    }

    @Test
    fun `Should throw when no CallId feature`() {
        assertThrows<IllegalStateException> {
            testServer(installCallId = false).start()
        }
    }
}
