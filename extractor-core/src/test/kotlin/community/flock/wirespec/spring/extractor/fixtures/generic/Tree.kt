package community.flock.wirespec.spring.extractor.fixtures.generic

class Tree<T>(
    val value: T,
    val children: List<Tree<T>>,
)
