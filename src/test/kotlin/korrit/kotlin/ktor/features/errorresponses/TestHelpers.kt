package korrit.kotlin.ktor.features.errorresponses

import com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath
import com.jayway.jsonpath.matchers.JsonPathMatchers.isJson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.contentType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.isA
import org.hamcrest.core.IsEqual

class Fixture(val field: String)
class SomeDomainException : RuntimeException("Domain message")

internal val asInt = isA<Int>(Int::class.java)
internal val asLong = isA<Long>(Long::class.java)
internal val asString = isA<String>(String::class.java)
internal val asBoolean = isA<Boolean>(Boolean::class.java)

internal fun TestApplicationCall.assertApiError(status: HttpStatusCode) {
    assertThat(response.status(), IsEqual(status))
    assertThat(response.contentType(), IsEqual(ContentType.Application.Json.withCharset(Charsets.UTF_8)))
    assertThat(response.content, isJson())
    assertThat(response.content, hasJsonPath("$.status", asInt))
    assertThat(response.content, hasJsonPath("$.type", asString))
    assertThat(response.content, hasJsonPath("$.title", asString))
    assertThat(response.content, hasJsonPath("$.detail", asString))
    assertThat(response.content, hasJsonPath("$.path", asString))
    assertThat(response.content, hasJsonPath("$.instance", asString))
    assertThat(response.content, hasJsonPath("$.timestamp", asString))
}
