package community.flock.wirespec.spring.extractor.fixtures.generic

data class ApiResponse<T>(val data: T, val status: Int)
