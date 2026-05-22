package community.flock.wirespec.spring.extractor.extract.kafka

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload

class KafkaPayloadSelectorTest {

    data class OrderEvent(val id: String)

    @Suppress("unused")
    class Fixtures {
        fun plain(event: OrderEvent) {}
        fun withRecord(rec: ConsumerRecord<String, OrderEvent>) {}
        fun withMessage(msg: Message<OrderEvent>) {}
        fun batch(events: List<OrderEvent>) {}
        fun withPayloadAnnotation(@Payload event: OrderEvent, @Header("k") key: String) {}
        fun nonMetaSinglePlusHeader(event: OrderEvent, @Header("k") key: String) {}
        fun ambiguousTwoPayloads(a: OrderEvent, b: OrderEvent) {}
        fun metaOnly(ack: Acknowledgment, consumer: Consumer<String, OrderEvent>) {}
    }

    private val cls = Fixtures::class.java

    private fun method(name: String) = cls.declaredMethods.first { it.name == name }

    @Test fun `plain single param`() {
        val r = KafkaPayloadSelector.select(method("plain"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `unwraps ConsumerRecord generic to V`() {
        val r = KafkaPayloadSelector.select(method("withRecord"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `unwraps Message generic to T`() {
        val r = KafkaPayloadSelector.select(method("withMessage"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `unwraps List for batch listener`() {
        val r = KafkaPayloadSelector.select(method("batch"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `Payload annotation wins over other params`() {
        val r = KafkaPayloadSelector.select(method("withPayloadAnnotation"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `single non-meta param when others are Header annotated`() {
        val r = KafkaPayloadSelector.select(method("nonMetaSinglePlusHeader"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Selected>()
        (r.payloadType as Class<*>) shouldBe OrderEvent::class.java
    }

    @Test fun `ambiguous returns Skipped`() {
        val r = KafkaPayloadSelector.select(method("ambiguousTwoPayloads"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Skipped>()
        (r as KafkaPayloadSelector.Result.Skipped).reason shouldBe "ambiguous payload parameter"
    }

    @Test fun `meta-only returns Skipped`() {
        val r = KafkaPayloadSelector.select(method("metaOnly"))
        r.shouldBeInstanceOf<KafkaPayloadSelector.Result.Skipped>()
    }
}
