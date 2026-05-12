package community.flock.wirespec.spring.extractor

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtractMojoTest {

    @Test
    fun `detectControllerCollisions returns empty map when no collision`() {
        val a = community.flock.wirespec.spring.extractor.fixtures.HelloController::class.java
        val b = community.flock.wirespec.spring.extractor.fixtures.ParamsController::class.java
        val collisions = detectControllerCollisions(listOf(a, b))
        collisions shouldBe emptyMap()
    }

    @Test
    fun `detectControllerCollisions reports same-simple-name controllers`() {
        // Simulate two entries of the same class (standing in for two classes with the same simpleName)
        val a = community.flock.wirespec.spring.extractor.fixtures.HelloController::class.java
        val collisions = detectControllerCollisions(listOf(a, a))
        collisions.keys shouldContainExactly setOf("HelloController")
        collisions["HelloController"]!!.size shouldBe 2
    }
}
