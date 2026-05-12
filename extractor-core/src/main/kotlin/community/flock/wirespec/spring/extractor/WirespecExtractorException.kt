package community.flock.wirespec.spring.extractor

/**
 * Thrown by [WirespecExtractor.extract] when extraction fails because of:
 * - a missing or empty classes directory
 * - a non-writable output directory
 * - two scanned controllers sharing a simple name
 */
class WirespecExtractorException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
