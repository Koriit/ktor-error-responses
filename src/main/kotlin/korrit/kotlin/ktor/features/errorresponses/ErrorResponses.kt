package korrit.kotlin.ktor.features.errorresponses

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.request.ApplicationReceivePipeline
import io.ktor.request.ApplicationReceiveRequest
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.coroutineScope
import java.util.*
import kotlin.reflect.full.primaryConstructor

typealias CallCallback = suspend PipelineContext<Unit, ApplicationCall>.(Throwable) -> Unit
typealias StatusCallback = suspend PipelineContext<Any, ApplicationCall>.(HttpStatusCode) -> Unit

/**
 * Error responses feature that handles exceptions and status codes.
 *
 * Based on Status Responses feature.
 */
class ErrorResponses(config: Configuration) {

    // Wrappers to pass exceptions from receive/send pipelines to application pipeline
    private class ReceivePipelineExceptionWrapper(wrapped: Throwable) : Exception(wrapped)
    private class SendPipelineExceptionWrapper(wrapped: Throwable) : Exception(wrapped)

    private val callExceptions = HashMap(config.callExceptions)
    private val receiveExceptions = HashMap(config.callExceptions + config.receiveExceptions)
    private val sendExceptions = HashMap(config.callExceptions + config.sendExceptions)
    private val statuses = HashMap(config.statuses)

    /**
     * Error responses feature config
     */
    class Configuration {

        /**
         * Exception handlers map by exception class
         */
        val receiveExceptions = mutableMapOf<Class<*>, CallCallback>()
        val sendExceptions = mutableMapOf<Class<*>, CallCallback>()
        val callExceptions = mutableMapOf<Class<*>, CallCallback>()

        /**
         * Status handlers by status code
         */
        val statuses = mutableMapOf<HttpStatusCode, StatusCallback>()

        /**
         * Register exception [handler] for exception type [T] and it's children
         */
        inline fun <reified T : Throwable> exception(
                noinline handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
        ) =
                exception(T::class.java, handler)

        /**
         * Register exception [handler] for exception class [klass] and it's children
         */
        fun <T : Throwable> exception(
                klass: Class<T>,
                handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            callExceptions.put(klass, handler as CallCallback)
        }

        /**
         * Register receiveException [handler] for exception type [T] and it's children
         */
        inline fun <reified T : Throwable> receiveException(
                noinline handler: suspend PipelineContext<ApplicationReceiveRequest, ApplicationCall>.(T) -> Unit
        ) =
                receiveException(T::class.java, handler)

        /**
         * Register receiveException [handler] for exception class [klass] and it's children
         */
        fun <T : Throwable> receiveException(
                klass: Class<T>,
                handler: suspend PipelineContext<ApplicationReceiveRequest, ApplicationCall>.(T) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            receiveExceptions.put(klass, handler as CallCallback)
        }

        /**
         * Register sendException [handler] for exception type [T] and it's children
         */
        inline fun <reified T : Throwable> sendException(
                noinline handler: suspend PipelineContext<Any, ApplicationCall>.(T) -> Unit
        ) =
                sendException(T::class.java, handler)

        /**
         * Register sendException [handler] for exception class [klass] and it's children
         */
        fun <T : Throwable> sendException(
                klass: Class<T>,
                handler: suspend PipelineContext<Any, ApplicationCall>.(T) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            sendExceptions.put(klass, handler as CallCallback)
        }

        /**
         * Register status [handler] for [status] code
         */
        fun status(vararg status: HttpStatusCode, handler: StatusCallback) {
            status.forEach {
                statuses.put(it, handler)
            }
        }

        /**
         * Helper function to register Exception Handlers
         */
        inline fun <reified T : Any> handler(configure: T.() -> Unit = {}) {
            T::class.primaryConstructor?.call(this)?.configure() ?: throw IllegalArgumentException("${T::class.java.name} handler does not define primary constructor with ${this.javaClass.name} argument")
        }
    }

    private suspend fun interceptResponse(context: PipelineContext<Any, ApplicationCall>, message: Any) {
        val call = context.call
        if (call.attributes.contains(key)) return

        val status = when (message) {
            is OutgoingContent -> message.status
            is HttpStatusCode -> message
            else -> null
        }
        if (status != null) {
            val handler = statuses[status]
            if (handler != null) {
                call.attributes.put(key, this@ErrorResponses)
                context.handler(status)
                finishIfResponseSent(context)
            }
        }
    }

    private fun finishIfResponseSent(context: PipelineContext<*, ApplicationCall>) {
        if (context.call.response.status() != null) {
            context.finish()
        }
    }

    private suspend fun interceptCall(context: PipelineContext<Unit, ApplicationCall>) {
        try {
            coroutineScope {
                context.proceed()
            }
        } catch (e: Throwable) {
            // If we handled those underlying exceptions inside receive/send pipelines then application pipeline
            // would just continue normally and without a doubt fail somewhere spectacularly
            val (exception, handlers) = when (e) {
                is ReceivePipelineExceptionWrapper -> e.cause!! to receiveExceptions
                is SendPipelineExceptionWrapper -> e.cause!! to sendExceptions
                else -> e to callExceptions
            }
            val handler = findHandlerByType(exception.javaClass, handlers)
            if (handler != null && context.call.response.status() == null) {
                context.handler(exception)
                finishIfResponseSent(context)
            } else
                throw exception
        }
    }

    private suspend fun wrapReceiveExceptions(context: PipelineContext<*, ApplicationCall>) {
        try {
            coroutineScope {
                context.proceed()
            }
        } catch (exception: Throwable) {
            val handler = findHandlerByType(exception.javaClass, receiveExceptions)
            if (handler != null) {
                throw ReceivePipelineExceptionWrapper(exception)
            } else
                throw exception
        }
    }

    private suspend fun wrapSendExceptions(context: PipelineContext<*, ApplicationCall>) {
        try {
            coroutineScope {
                context.proceed()
            }
        } catch (exception: Throwable) {
            val handler = findHandlerByType(exception.javaClass, sendExceptions)
            if (handler != null) {
                throw ReceivePipelineExceptionWrapper(exception)
            } else
                throw exception
        }
    }

    private fun findHandlerByType(clazz: Class<*>, exceptions: HashMap<Class<*>, CallCallback>): (CallCallback)? {
        exceptions[clazz]?.let { return it }
        clazz.superclass?.let {
            findHandlerByType(it, exceptions)?.let { return it }
        }
        clazz.interfaces.forEach {
            findHandlerByType(it, exceptions)?.let { return it }
        }
        return null
    }

    /**
     * Feature installation object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ErrorResponses> {

        override val key = AttributeKey<ErrorResponses>("Error Responses")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ErrorResponses {
            val configuration = Configuration().apply(configure)
            val feature = ErrorResponses(configuration)
            if (feature.statuses.isNotEmpty()) {
                pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
                    feature.interceptResponse(this, message)
                }
            }
            if (feature.callExceptions.isNotEmpty()) {
                pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                    feature.interceptCall(this)
                }
            }
            if (feature.receiveExceptions.isNotEmpty()) {
                pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
                    feature.wrapReceiveExceptions(this)
                }
            }
            if (feature.sendExceptions.isNotEmpty()) {
                pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
                    feature.wrapSendExceptions(this)
                }
            }
            return feature
        }
    }
}
