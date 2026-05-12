# wirespec-spring-extractor

A Maven and Gradle plugin that scans a Spring Boot application's compiled classes and emits
[Wirespec](https://wirespec.io) (`.ws`) files describing its HTTP endpoints and DTO types.

## Usage (Maven)

Drop the plugin into `pom.xml` with `<extensions>true</extensions>` and it
auto-binds to `process-classes`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>community.flock.wirespec.spring</groupId>
      <artifactId>wirespec-spring-extractor-maven-plugin</artifactId>
      <version>0.1.0</version>
      <extensions>true</extensions>
      <configuration>
        <!-- optional — defaults to ${project.build.directory}/wirespec -->
        <output>${project.build.directory}/wirespec</output>
        <!-- optional — only scan classes under this package -->
        <basePackage>com.acme.api</basePackage>
      </configuration>
    </plugin>
  </plugins>
</build>
```

`mvn package` (or any goal from `process-classes` onward) writes `.ws`
files into `target/wirespec/`. To trigger the goal directly:

```bash
mvn wirespec:extract
```

## Usage (Gradle)

### Kotlin project

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    id("community.flock.wirespec.spring.extractor") version "0.1.0"
}

dependencies {
    implementation("org.springframework:spring-web:6.1.14")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // So Spring's @PathVariable/@RequestParam (and the extractor) can
        // recover parameter names that have no explicit value().
        freeCompilerArgs.add("-java-parameters")
    }
}

wirespecExtractor {
    // optional — defaults to build/wirespec
    // outputDir.set(layout.buildDirectory.dir("wirespec"))

    // optional — only scan classes under this package
    basePackage.set("com.acme.api")
}
```

### Java project

```kotlin
plugins {
    java
    id("community.flock.wirespec.spring.extractor") version "0.1.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("org.springframework:spring-web:6.1.14")
}

// So Spring's @PathVariable/@RequestParam (and the extractor) can recover
// parameter names that have no explicit value().
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

wirespecExtractor {
    basePackage.set("com.acme.api")
}
```

Applying the plugin alongside any JVM source plugin auto-wires
`extractWirespec` into `assemble`, so `gradle build` (or `gradle assemble`)
writes `.ws` files into `build/wirespec/`. To trigger it directly:

```bash
./gradlew extractWirespec
```

> ⚠️ **Compile with `-parameters` / `-java-parameters`.** Without it the
> JVM erases method parameter names, and `@PathVariable Long id` —
> declared without an explicit `value` — comes out as `arg0`. Both
> fixture builds in this repo set the flag, and you should too.

## What it extracts

- `@RestController` / `@Controller` (with `@ResponseBody`) classes
- The `@RequestMapping` family (`@GetMapping`, `@PostMapping`, etc.)
- Path / query / header / cookie parameters; `@RequestBody`
- Response types, including unwrapping of `ResponseEntity`, `Mono`, `Flux`,
  `Optional`, `Callable`, `DeferredResult`
- DTO classes referenced by endpoints, with Jackson (`@JsonProperty`,
  `@JsonIgnore`), Bean Validation (`@NotNull`, `@Size`, `@Pattern`, `@Min`,
  `@Max`), and springdoc `@Schema` awareness

### Known limitations (v1)

- `@Controller` classes with handler methods directly annotated with
  `@ResponseBody`. Meta-annotated variants (e.g., custom annotations that are
  themselves annotated with `@ResponseBody`) are not detected in v1.

See [docs/superpowers/specs/2026-05-12-wirespec-spring-extractor-design.md](docs/superpowers/specs/2026-05-12-wirespec-spring-extractor-design.md)
for the full design and current scope limitations.

## Output layout

- One `<ControllerName>.ws` per controller (endpoints)
- One shared `types.ws` (all referenced DTO types)
- The output directory is treated as a generated artifact: existing `*.ws`
  files are deleted on each run; non-`.ws` files are left alone.
