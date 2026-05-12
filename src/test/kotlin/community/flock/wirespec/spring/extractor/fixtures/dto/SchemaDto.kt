package community.flock.wirespec.spring.extractor.fixtures.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.util.Optional

data class SchemaDto(
    @field:Schema(description = "The user's display name", required = true)
    @field:NotNull
    val name: String,

    val maybe: Optional<String>,

    val nullable: String?,
    val notNullablePrimitive: Int,
)
