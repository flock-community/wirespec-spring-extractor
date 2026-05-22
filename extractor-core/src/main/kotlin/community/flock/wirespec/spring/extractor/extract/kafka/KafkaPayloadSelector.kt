package community.flock.wirespec.spring.extractor.extract.kafka

import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Pure logic: given a `@KafkaListener` (or `@KafkaHandler`) method, pick the
 * payload parameter and unwrap framework wrappers down to the value type.
 *
 * Spring Kafka is NOT on extractor-core's main classpath. We identify
 * meta parameters and framework wrappers by string FQN comparison against
 * the parameter's `Class.name`.
 */
internal object KafkaPayloadSelector {

    private const val PAYLOAD_ANNOTATION = "org.springframework.messaging.handler.annotation.Payload"
    private const val HEADER_ANNOTATION  = "org.springframework.messaging.handler.annotation.Header"
    private const val HEADERS_ANNOTATION = "org.springframework.messaging.handler.annotation.Headers"

    private const val CONSUMER_RECORD_FQN = "org.apache.kafka.clients.consumer.ConsumerRecord"
    private const val MESSAGE_FQN         = "org.springframework.messaging.Message"

    private const val ACKNOWLEDGMENT_FQN = "org.springframework.kafka.support.Acknowledgment"
    private const val CONSUMER_FQN       = "org.apache.kafka.clients.consumer.Consumer"

    sealed interface Result {
        /** Payload param picked; [payloadType] is post-unwrap. */
        data class Selected(val payloadType: Type) : Result
        data class Skipped(val reason: String) : Result
    }

    fun select(method: Method): Result {
        val params = method.parameters.toList()
        if (params.isEmpty()) return Result.Skipped("no parameters")

        // 1. @Payload wins.
        val payloadAnnotated = params.firstOrNull { p ->
            p.annotations.any { it.annotationClass.java.name == PAYLOAD_ANNOTATION }
        }
        if (payloadAnnotated != null) return unwrap(payloadAnnotated.parameterizedType)

        // 2. Exactly one non-meta parameter.
        val nonMeta = params.filter { !isMeta(it) }
        return when (nonMeta.size) {
            1    -> unwrap(nonMeta.single().parameterizedType)
            0    -> Result.Skipped("no payload parameter")
            else -> Result.Skipped("ambiguous payload parameter")
        }
    }

    private fun isMeta(p: Parameter): Boolean {
        if (p.annotations.any {
                val n = it.annotationClass.java.name
                n == HEADER_ANNOTATION || n == HEADERS_ANNOTATION
            }) return true
        val raw = (p.parameterizedType as? Class<*>) ?: (p.parameterizedType as? ParameterizedType)?.rawType as? Class<*>
        val name = raw?.name ?: return false
        return name == ACKNOWLEDGMENT_FQN || name == CONSUMER_FQN
    }

    private fun unwrap(t: Type): Result {
        if (t is WildcardType) return Result.Skipped("wildcard payload")
        if (t is Class<*>) {
            // Raw List/ConsumerRecord/Message with no type args → cannot recover V.
            if (t.name == CONSUMER_RECORD_FQN || t.name == MESSAGE_FQN || t.name == "java.util.List") {
                return Result.Skipped("raw ${t.simpleName} payload")
            }
            return Result.Selected(t)
        }
        if (t is ParameterizedType) {
            val raw = (t.rawType as? Class<*>) ?: return Result.Skipped("unrecognised payload type")
            return when (raw.name) {
                CONSUMER_RECORD_FQN -> unwrap(t.actualTypeArguments.getOrNull(1) ?: return Result.Skipped("ConsumerRecord without V"))
                MESSAGE_FQN         -> unwrap(t.actualTypeArguments.getOrNull(0) ?: return Result.Skipped("Message without T"))
                "java.util.List"    -> unwrap(t.actualTypeArguments.getOrNull(0) ?: return Result.Skipped("List without T"))
                else                -> Result.Selected(t)
            }
        }
        return Result.Skipped("unrecognised payload type ${t.typeName}")
    }
}
