# wirespec-spring-extractor-maven-plugin

A Maven plugin that scans a Spring Boot application's compiled classes and emits
[Wirespec](https://wirespec.io) (`.ws`) files describing its HTTP endpoints and DTO types.

## Usage

```xml
<plugin>
  <groupId>community.flock.wirespec.spring</groupId>
  <artifactId>wirespec-spring-extractor-maven-plugin</artifactId>
  <version>0.1.0</version>
  <configuration>
    <output>${project.build.directory}/wirespec</output>
    <!-- optional: only scan classes under this package -->
    <basePackage>com.acme.api</basePackage>
  </configuration>
</plugin>
```

The plugin auto-binds to the `process-classes` phase, so a normal `mvn compile`
will produce `.ws` files in `target/wirespec/`. To run it manually:

```bash
mvn wirespec:extract
```

## What it extracts

- `@RestController` / `@Controller` (with `@ResponseBody`) classes
- The `@RequestMapping` family (`@GetMapping`, `@PostMapping`, etc.)
- Path / query / header / cookie parameters; `@RequestBody`
- Response types, including unwrapping of `ResponseEntity`, `Mono`, `Flux`,
  `Optional`, `Callable`, `DeferredResult`
- DTO classes referenced by endpoints, with Jackson (`@JsonProperty`,
  `@JsonIgnore`), Bean Validation (`@NotNull`, `@Size`, `@Pattern`, `@Min`,
  `@Max`), and springdoc `@Schema` awareness

See [docs/superpowers/specs/2026-05-12-wirespec-spring-extractor-design.md](docs/superpowers/specs/2026-05-12-wirespec-spring-extractor-design.md)
for the full design and current scope limitations.

## Output layout

- One `<ControllerName>.ws` per controller (endpoints)
- One shared `types.ws` (all referenced DTO types)
- The output directory is treated as a generated artifact: existing `*.ws`
  files are deleted on each run; non-`.ws` files are left alone.
