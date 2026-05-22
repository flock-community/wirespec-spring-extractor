package community.flock.wirespec.spring.extractor.extract.kafka

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate

class KafkaProducerScannerTest {

    data class Order(val id: String)
    data class Shipment(val id: String)

    @Suppress("unused")
    class GoodPublisher(
        private val orders: KafkaTemplate<String, Order>,
        private val shipments: KafkaTemplate<String, Shipment>,
    )

    @Suppress("unused")
    class RawPublisher(private val raw: KafkaTemplate<*, *>)

    @Test
    fun `recovers V for typed KafkaTemplate fields`() {
        val sites = KafkaProducerScanner.scan(
            classLoader = javaClass.classLoader,
            scanPackages = listOf("community.flock.wirespec.spring.extractor.extract.kafka"),
            basePackage = null,
        )
        val good = sites.filter { it.ownerClass == GoodPublisher::class.java }
        good shouldHaveSize 2
        good.map { it.valueClass.simpleName }.toSet() shouldBe setOf("Order", "Shipment")
    }

    @Test
    fun `drops raw KafkaTemplate fields`() {
        val sites = KafkaProducerScanner.scan(
            classLoader = javaClass.classLoader,
            scanPackages = listOf("community.flock.wirespec.spring.extractor.extract.kafka"),
            basePackage = null,
        )
        sites.none { it.ownerClass == RawPublisher::class.java } shouldBe true
    }
}
