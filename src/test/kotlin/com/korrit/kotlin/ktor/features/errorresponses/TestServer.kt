package com.korrit.kotlin.ktor.features.errorresponses

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.korrit.kotlin.ktor.convertTime
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.testing.TestApplicationEngine
import java.util.UUID

fun testServer(
    installCallId: Boolean = true
): TestApplicationEngine {
    return TestApplicationEngine(
        applicationEngineEnvironment {
            val jackson = jackson()

            module {
                if (installCallId) {
                    install(CallId) {
                        header(HttpHeaders.XRequestId)
                        generate { UUID.randomUUID().toString() }
                        verify { it.isNotBlank() }
                    }
                }
                install(DataConversion) {
                    convertTime(jackson)
                }
                install(ContentNegotiation) {
                    register(Application.Json, JacksonConverter(jackson))
                }

                install(ErrorResponses) {
                    handler<DefaultExceptionHandler> {
                        // Domain
                        register<SomeDomainException>(HttpStatusCode.UnprocessableEntity)
                        // Jackson
                        registerReceive<JsonProcessingException>(HttpStatusCode.BadRequest)
                        registerSend<JsonProcessingException>(HttpStatusCode.Conflict)
                    }
                }

                routing {
                    get("/no-content") {
                        call.respond(HttpStatusCode.NoContent, "")
                    }
                    get("/global-error") {
                        throw SomeDomainException()
                    }
                    get("/unmapped-error") {
                        jackson.readValue<Fixture>("")
                    }
                    get("/send-error") {
                        call.respond(this)
                    }
                    post("/receive-error") {
                        call.respond(call.receive<Fixture>())
                    }
                }
            }
        }
    )
}

internal fun jackson() = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()
