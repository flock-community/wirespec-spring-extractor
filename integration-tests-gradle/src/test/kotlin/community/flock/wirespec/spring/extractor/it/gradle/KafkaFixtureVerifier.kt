package community.flock.wirespec.spring.extractor.it.gradle

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * Verifier for the `kafka-app` fixture. Asserts that @KafkaListener and
 * @KafkaHandler methods produce `channel` definitions in the per-class .ws
 * file, and that shared payload DTOs float to types.ws via TypeOwnership.
 */
object KafkaFixtureVerifier {

    fun verify(wsDir: File) {
        assertTrue(wsDir.isDirectory) { "wirespec output dir missing at ${wsDir.absolutePath}" }

        val files = wsDir.listFiles()!!.map { it.name }.sorted()
        files.shouldContainExactly("OrderConsumer.ws", "ShipmentRouter.ws", "types.ws")

        val consumer = File(wsDir, "OrderConsumer.ws").readText()
        consumer shouldContain "channel OnOrderCreated -> OrderEvent"
        consumer shouldContain "channel OnOrderRecord -> OrderEvent"
        consumer shouldContain "channel OnOrderMessage -> OrderEvent"
        consumer shouldContain "channel OnOrderBatch -> OrderEvent"
        consumer shouldContain "channel OnOrderWithHeader -> OrderEvent"

        // OrderEvent is referenced by BOTH OrderConsumer.ws and ShipmentRouter.ws —
        // TypeOwnership floats it into types.ws.
        val types = File(wsDir, "types.ws").readText()
        types shouldContain "type OrderEvent"

        val shipment = File(wsDir, "ShipmentRouter.ws").readText()
        shipment shouldContain "channel OnShipmentCreated -> ShipmentEvent"
        shipment shouldContain "channel OnOrderShipped -> OrderEvent"
        // ShipmentEvent has only one owner — it stays inline.
        shipment shouldContain "type ShipmentEvent"

        // OrderEvent must NOT appear in either per-class file when it's shared.
        assertTrue(!Regex("(?m)^\\s*type\\s+OrderEvent\\b").containsMatchIn(consumer)) {
            "OrderEvent leaked into OrderConsumer.ws despite being shared:\n$consumer"
        }
        assertTrue(!Regex("(?m)^\\s*type\\s+OrderEvent\\b").containsMatchIn(shipment)) {
            "OrderEvent leaked into ShipmentRouter.ws despite being shared:\n$shipment"
        }
    }
}
