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

