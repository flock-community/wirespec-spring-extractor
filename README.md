# wirespec-spring-extractor-maven-plugin

A Maven plugin that scans a Spring Boot application's compiled classes and emits
[Wirespec](https://wirespec.io) (`.ws`) files describing its HTTP endpoints and DTO types.

## Usage

```xml
<plugin>
  <groupId>community.flock.wirespec.spring</groupId>
  <artifactId>wirespec-spring-extractor-maven-plugin</artifactId>
  <version>0.1.0</version>
  <extensions>true</extensions>
  <configuration>
    <output>${project.build.directory}/wirespec</output>
    <!-- optional: only scan classes under this package -->
    <basePackage>com.acme.api</basePackage>
  </configuration>
</plugin>
```

With `<extensions>true</extensions>` the plugin's Maven lifecycle participant
auto-binds the `extract` goal to the `process-classes` phase — no
`<executions>` block needed. A normal `mvn package` (or `install`, `verify`,
`test`, `process-classes` — anything from `process-classes` onward) produces
`.ws` files in `target/wirespec/`.

> ⚠️ **`mvn compile` is *not* enough.** Maven's `compile` phase finishes one
> step before `process-classes`, so the plugin is not yet triggered. Use
> `mvn process-classes` as the minimum, or invoke
> `mvn compile wirespec:extract` to run both explicitly. `mvn test`,
> `mvn package`, `mvn install`, etc. all include `process-classes` and will
> run the plugin automatically.

To run it manually outside the lifecycle:

```bash
mvn wirespec:extract
```

If you'd rather bind explicitly (e.g. to a different phase, or to skip the
extension mechanism), drop `<extensions>true</extensions>` and declare the
execution yourself:

```xml
<executions>
  <execution>
    <goals><goal>extract</goal></goals>
  </execution>
</executions>
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
