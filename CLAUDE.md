# Claude Development Guidelines

## For general instructions

@README.md

## For release instructions

@RELEASE.md

## Exception Handling

**NEVER use catch-all exception handlers like `catch (e: Exception)`**

### Why catch-all is bad:
- Hides real bugs and unexpected errors
- Makes debugging nearly impossible 
- Swallows important error information
- Can mask serious issues like OutOfMemoryError, etc.

### What to do instead:
- Catch specific exceptions that you know how to handle
- Let unexpected exceptions bubble up with proper error messages
- Use meaningful fallback behavior only for known failure cases

### Examples:

**❌ BAD - catch-all:**
```kotlin
try {
  startingDir.relativize(dir)
} catch (e: Exception) {
  dir
}
```

**✅ GOOD - specific exceptions:**
```kotlin
try {
  startingDir.relativize(dir) 
} catch (e: IllegalArgumentException) {
  // Different file system roots - use absolute path
  dir
}
```

**❌ BAD - hiding all errors:**
```kotlin
try {
  val normalizedFile = filePath.toAbsolutePath().normalize()
  normalizedFile.startsWith(normalizedWorkingDir)
} catch (e: Exception) {
  false
}
```

**✅ GOOD - specific handling:**
```kotlin
try {
  val normalizedFile = filePath.toAbsolutePath().normalize()
  normalizedFile.startsWith(normalizedWorkingDir)
} catch (e: InvalidPathException) {
  false // Invalid path syntax
} catch (e: IOError) {
  false // File system access error
}
```

## Function Style

**Use function expressions for simple delegating functions**

### Why function expressions are better:
- More concise and readable
- Clearly shows the function is just a simple wrapper
- Less boilerplate code
- More idiomatic Kotlin

### Examples:

**❌ BAD - unnecessary function body:**
```kotlin
internal fun loadKtfmtIgnore(): List<String> {
  return loadKtfmtIgnore(workingDirectory)
}
```

**✅ GOOD - function expression:**
```kotlin
internal fun loadKtfmtIgnore(): List<String> = loadKtfmtIgnore(workingDirectory)
```

**❌ BAD - simple return:**
```kotlin
private fun isKotlinFile(path: Path): Boolean {
  return fileName.endsWith(".kt") || fileName.endsWith(".kts")
}
```

**✅ GOOD - function expression:**
```kotlin
private fun isKotlinFile(path: Path): Boolean = 
  fileName.endsWith(".kt") || fileName.endsWith(".kts")
```

### When to use function bodies:
- Functions with multiple statements
- Functions with complex logic
- Functions where you need local variables

## Process Exit

**Use Kotlin's `exitProcess()` instead of Java's `System.exit()`**

### Why Kotlin style is better:
- More idiomatic Kotlin
- IntelliJ/IDE warnings are avoided
- Clearer intent that we're exiting the process
- Consistent with Kotlin naming conventions

### Examples:

**❌ BAD - Java style:**
```kotlin
System.exit(1)
```

**✅ GOOD - Kotlin style:**
```kotlin
import kotlin.system.exitProcess
exitProcess(1)
```

## Formatting

**ALWAYS use Gradle for formatting - NEVER use the ktfmt-cli command line tool**

### Why ALWAYS use Gradle:
- The ktfmt-cli is the product we're developing, not a tool for development
- Gradle ktfmt plugin ensures consistent formatting with project configuration
- Prevents confusion between testing the CLI and using it for development
- Avoids circular dependency issues

### What to use:
```bash
# ALWAYS use Gradle for formatting
./gradlew ktfmtFormat

# To check formatting
./gradlew ktfmtCheck

# Build includes formatting check
./gradlew build
```

### What NOT to use:
```bash
# ❌ NEVER use this for development
./ktfmt-cli --write
```

## Filesystem Usage

**NEVER use `Paths.get()` - always use `fileSystem.getPath()` when available**

### Why fileSystem.getPath() is better:
- Ensures consistent filesystem usage (especially important for tests using Jimfs)
- Prevents bugs where different filesystem instances are used
- Makes code testable with in-memory filesystems
- Avoids issues where cache files are written to one filesystem but read from another

### Examples:

**❌ BAD - Using default filesystem:**
```kotlin
val cacheManager = cacheLocation?.let { CacheManager(Paths.get(it)) }
```

**✅ GOOD - Using provided filesystem:**
```kotlin
val cacheManager = cacheLocation?.let { CacheManager(fileSystem.getPath(it)) }
```

### When to use each:
- Use `fileSystem.getPath()` when you have access to a FileSystem instance (e.g., in App class)
- Only use `Paths.get()` in main functions or when no FileSystem instance is available
- Always prefer dependency injection of FileSystem for testability (i.e. through Jimfs in tests)

## Release Process

**Update version in `gradle.properties`, not `build.gradle.kts`**

### Why use gradle.properties:
- Configuration cache compatible with provider API
- Separates version configuration from build logic
- Reduces configuration cache invalidation
- Follows Gradle best practices

### Pre-release checklist:
1. **ALWAYS run formatting BEFORE releasing**:
   ```bash
   ./gradlew ktfmtFormat
   ```
2. **Run tests**:
   ```bash
   ./gradlew test
   ```
3. **Update version in gradle.properties**
4. **Build and verify**:
   ```bash
   ./gradlew clean build
   ```

### Version configuration in build.gradle.kts:
```kotlin
// ✅ GOOD - Configuration cache compatible
val cliVersion = providers.gradleProperty("cliVersion")
val ktfmtVersion = providers.gradleProperty("ktfmtVersion")
val fullVersion = cliVersion.zip(ktfmtVersion) { cli, ktfmt -> "$cli-ktfmt$ktfmt" }

// ❌ BAD - Not configuration cache compatible
val cliVersion: String by project
val ktfmtVersion = "0.53" // hardcoded
```

For detailed release steps, see @RELEASE.md

