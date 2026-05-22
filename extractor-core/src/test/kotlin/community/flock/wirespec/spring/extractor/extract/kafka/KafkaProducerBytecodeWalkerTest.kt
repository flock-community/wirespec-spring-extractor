package community.flock.wirespec.spring.extractor.extract.kafka

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate

class KafkaProducerBytecodeWalkerTest {

    data class Order(val id: String)
    data class Shipment(val id: String)

    @Suppress("unused")
    class Publisher(
        private val orders: KafkaTemplate<String, Order>,
        private val shipments: KafkaTemplate<String, Shipment>,
    ) {
        fun publishOrder(order: Order) {
            orders.send("orders.created", order)
        }

        fun publishOrderTwice(order: Order) {
            orders.send("orders.created", order)
            orders.send("orders.updated", order)
        }

        fun publishShipment(shipment: Shipment) {
            shipments.send("shipments.created", shipment)
        }

        fun noSend(order: Order) {
            // does not call send
        }
    }

    @Test
    fun `discovers one channel per (method, value-type) tuple`() {
        val fields = listOf(
            KafkaProducerScanner.TemplateField(Publisher::class.java, "orders", Order::class.java),
            KafkaProducerScanner.TemplateField(Publisher::class.java, "shipments", Shipment::class.java),
        )
        val sites = KafkaProducerBytecodeWalker.walk(Publisher::class.java, fields)
        sites shouldHaveSize 3
        sites.map { it.enclosingMethod to it.valueClass.simpleName }.toSet() shouldBe setOf(
            "publishOrder" to "Order",
            "publishOrderTwice" to "Order",
            "publishShipment" to "Shipment",
        )
    }
}
