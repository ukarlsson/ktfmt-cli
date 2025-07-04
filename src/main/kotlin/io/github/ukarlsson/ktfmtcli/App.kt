package io.github.ukarlsson.ktfmtcli

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue
import kotlinx.cli.*

class KtfmtCliException(message: String) : Exception(message)

class App(
  private val fileSystem: FileSystem = FileSystems.getDefault(),
  private val workingDirectory: Path = Paths.get("").toAbsolutePath(),
) {

  fun run(args: Array<String>): Int {
    val parser = ArgParser("ktfmt-cli")

    val style by
      parser
        .option(ArgType.String, description = "Formatting style: meta (default), google, or kotlinlang")
        .default("meta")
    val maxWidth by
      parser
        .option(ArgType.Int, fullName = "max-width", shortName = "w", description = "Maximum line width")
        .default(100)
    val blockIndent by
      parser.option(
        ArgType.Int,
        fullName = "block-indent",
        description = "Block indent size in spaces (overrides style default)",
      )
    val continuationIndent by
      parser.option(
        ArgType.Int,
        fullName = "continuation-indent",
        description = "Continuation indent size in spaces (overrides style default)",
      )
    val removeUnusedImports by
      parser
        .option(ArgType.Boolean, fullName = "remove-unused-imports", description = "Remove unused imports")
        .default(true)
    val manageTrailingCommas by
      parser.option(
        ArgType.Boolean,
        fullName = "manage-trailing-commas",
        description = "Automatically add/remove trailing commas (defaults based on style)",
      )

    val write by parser.option(ArgType.Boolean, description = "Write formatting changes to files").default(false)
    val check by
      parser.option(ArgType.Boolean, description = "Check if files need formatting, exit 1 if so").default(false)
    val version by
      parser.option(ArgType.Boolean, shortName = "V", description = "Print version information").default(false)
    val debug by
      parser.option(ArgType.Boolean, description = "Enable debug output with timing information").default(false)
    val concurrency by
      parser
        .option(ArgType.Int, shortName = "j", description = "Number of parallel threads for formatting")
        .default(Runtime.getRuntime().availableProcessors())

    val globs by
      parser
        .argument(ArgType.String, description = "Files, directories, or glob patterns to format")
        .optional()
        .vararg()

    parser.parse(args)

    // Check if any globs look like options (start with -)
    val suspiciousGlobs = globs.filter { it.startsWith("-") }
    if (suspiciousGlobs.isNotEmpty()) {
      System.err.println("Error: Unknown option(s): ${suspiciousGlobs.joinToString(", ")}")
      return 1
    }

    if (version) {
      printVersion()
      return 0
    }

    val actualGlobs = if (globs.isEmpty()) listOf("**/*") else globs.toList()

    return runFormatting(
      actualGlobs,
      style,
      maxWidth,
      blockIndent,
      continuationIndent,
      removeUnusedImports,
      manageTrailingCommas,
      write,
      check,
      debug,
      concurrency,
    )
  }

  private fun runFormatting(
    globs: List<String>,
    style: String,
    maxWidth: Int,
    blockIndent: Int?,
    continuationIndent: Int?,
    removeUnusedImports: Boolean,
    manageTrailingCommas: Boolean?,
    write: Boolean,
    check: Boolean,
    debug: Boolean,
    concurrency: Int,
  ): Int {
    // Validate flags
    val modeCount = listOf(write, check).count { it }
    if (modeCount == 0) {
      System.err.println("Error: Must specify one of --write or --check")
      return 1
    }
    if (modeCount > 1) {
      System.err.println("Error: Cannot specify multiple modes (--write, --check)")
      return 1
    }

    val (files, collectionTime) = measureTimedValue { collectFiles(workingDirectory, globs, debug) }
    if (debug) {
      println("Debug: Collected ${files.size} files in $collectionTime")
    }

    // Get formatting options based on style, with explicit options taking precedence
    val (defaultBlockIndent, defaultContinuationIndent, defaultTrailingCommas) =
      when (style) {
        "google" -> Triple(2, 2, true)
        "kotlinlang" -> Triple(4, 4, true)
        "meta" -> Triple(2, 4, false)
        else -> Triple(2, 4, false) // Default to meta
      }

    val formattingOptions =
      FormattingOptions(
        blockIndent = blockIndent ?: defaultBlockIndent,
        continuationIndent = continuationIndent ?: defaultContinuationIndent,
        maxWidth = maxWidth,
        removeUnusedImports = removeUnusedImports,
        manageTrailingCommas = manageTrailingCommas ?: defaultTrailingCommas,
      )

    return when {
      write -> {
        val (result, formatTime) = measureTimedValue { processWrite(files, formattingOptions, concurrency) }
        if (debug) {
          println("Debug: Formatting took $formatTime")
        }
        result
      }
      check -> {
        val (result, formatTime) = measureTimedValue { processCheck(files, formattingOptions, concurrency) }
        if (debug) {
          println("Debug: Checking took $formatTime")
        }
        result
      }
      else -> 1
    }
  }

  private fun printVersion() =
    try {
      val props = Properties()
      val inputStream = this::class.java.classLoader.getResourceAsStream("version.properties")
      props.load(inputStream)

      val version = props.getProperty("version", "unknown")
      val ktfmtVersion = props.getProperty("ktfmt.version", "unknown")
      val cliVersion = props.getProperty("cli.version", "unknown")

      println("ktfmt-cli $version")
      println("CLI version: $cliVersion")
      println("ktfmt version: $ktfmtVersion")
    } catch (e: java.io.IOException) {
      println("ktfmt-cli version unknown")
    }

  private fun isKotlinFile(path: Path): Boolean {
    val fileName = path.fileName.toString()
    return fileName.endsWith(".kt") || fileName.endsWith(".kts")
  }

  private fun isValidKotlinFile(path: Path, ignorePatterns: List<String>, baseDir: Path): Boolean =
    Files.isRegularFile(path) &&
      isKotlinFile(path) &&
      isUnderWorkingDirectory(path) &&
      !isIgnored(path, ignorePatterns, baseDir)

  private fun collectFromPath(path: Path, originalGlob: String, ignorePatterns: List<String>): List<Path> {
    if (!isUnderWorkingDirectory(path)) {
      if (Files.isDirectory(path)) {
        throw KtfmtCliException("Directory '$originalGlob' is outside the working directory")
      } else {
        throw KtfmtCliException("File '$originalGlob' is outside the working directory")
      }
    }

    return when {
      Files.isRegularFile(path) -> {
        if (isValidKotlinFile(path, ignorePatterns, workingDirectory)) listOf(path) else emptyList()
      }
      Files.isDirectory(path) -> {
        val results = mutableListOf<Path>()
        Files.walkFileTree(
          path,
          object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
              // Skip the starting directory check, but check all subdirectories
              if (dir != path && isIgnored(dir, ignorePatterns, workingDirectory)) {
                return FileVisitResult.SKIP_SUBTREE
              }
              // Security check: ensure directory is under working directory
              if (!isUnderWorkingDirectory(dir)) {
                return FileVisitResult.SKIP_SUBTREE
              }
              return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
              if (isValidKotlinFile(file, ignorePatterns, workingDirectory)) {
                results.add(file)
              }
              return FileVisitResult.CONTINUE
            }
          },
        )
        results
      }
      else -> {
        throw KtfmtCliException("'$originalGlob' is not a file or directory")
      }
    }
  }

  internal fun extractBaseDirectory(glob: String, startingDir: Path): Pair<Path, String> {
    val globChars = setOf('*', '?', '[', '{')
    val pathComponents = glob.split('/')

    // Find the first component that contains glob characters
    var baseComponents = mutableListOf<String>()
    var remainingComponents = pathComponents

    for (i in pathComponents.indices) {
      val component = pathComponents[i]
      if (component.any { it in globChars }) {
        // Found the first component with glob characters
        remainingComponents = pathComponents.drop(i)
        break
      }
      baseComponents.add(component)
    }

    // If no glob characters found, treat entire path as literal
    if (baseComponents.size == pathComponents.size) {
      return Pair(startingDir, glob)
    }

    // Build the base directory path
    val baseDir =
      if (baseComponents.isEmpty()) {
        startingDir
      } else {
        val basePath = baseComponents.joinToString("/")
        if (Paths.get(basePath).isAbsolute) {
          Paths.get(basePath)
        } else {
          startingDir.resolve(basePath)
        }
      }

    val remainingPattern = remainingComponents.joinToString("/")
    return Pair(baseDir, remainingPattern)
  }

  private fun collectFromGlob(
    glob: String,
    startingDir: Path,
    ignorePatterns: List<String>,
    debug: Boolean = false,
  ): List<Path> {
    // Extract the base directory from the glob pattern to optimize traversal
    val (baseDir, remainingPattern) = extractBaseDirectory(glob, startingDir)

    if (!Files.exists(baseDir)) {
      return emptyList()
    }

    val pathMatcher = fileSystem.getPathMatcher("glob:$remainingPattern")
    val results = mutableListOf<Path>()

    Files.walkFileTree(
      baseDir,
      object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          // Skip the starting directory check, but check all subdirectories
          if (dir != baseDir) {
            val ignored = isIgnored(dir, ignorePatterns, startingDir)
            if (debug && ignored) {
              val relativePath =
                try {
                  startingDir.relativize(dir)
                } catch (e: IllegalArgumentException) {
                  // Different file system roots - use absolute path
                  dir
                }
              println("Debug: Skipping directory $relativePath (matched ignore pattern)")
            }
            if (ignored) {
              return FileVisitResult.SKIP_SUBTREE
            }
          }
          // Security check: ensure directory is under working directory
          if (!isUnderWorkingDirectory(dir)) {
            return FileVisitResult.SKIP_SUBTREE
          }
          return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (isKotlinFile(file)) {
            val relativePath = baseDir.relativize(file)
            if (
              pathMatcher.matches(relativePath) &&
                isUnderWorkingDirectory(file) &&
                !isIgnored(file, ignorePatterns, startingDir)
            ) {
              results.add(file)
            }
          }
          return FileVisitResult.CONTINUE
        }
      },
    )

    return results
  }

  internal fun collectFiles(startingDir: Path, globs: List<String>, debug: Boolean = false): List<Path> {
    val (ignorePatterns, ignoreTime) = measureTimedValue { loadKtfmtIgnore() }
    if (debug) {
      println("Debug: Loading ignore patterns took $ignoreTime")
    }

    return globs
      .flatMap { glob ->
        val (files, globTime) =
          measureTimedValue {
            val resolvedPath =
              if (Paths.get(glob).isAbsolute) {
                // Use absolute paths as-is (e.g., "/home/user/App.kt")
                Paths.get(glob)
              } else {
                // Make relative paths absolute (e.g., "src/App.kt" -> "/project/src/App.kt")
                startingDir.resolve(glob)
              }

            when {
              Files.exists(resolvedPath) -> {
                collectFromPath(resolvedPath, glob, ignorePatterns)
              }
              else -> {
                // Path doesn't exist, treat as glob pattern
                collectFromGlob(glob, startingDir, ignorePatterns, debug)
              }
            }
          }
        if (debug) {
          println("Debug: Processing glob '$glob' found ${files.size} files in $globTime")
        }
        files
      }
      .distinct()
  }

  internal fun loadKtfmtIgnore(): List<String> = loadKtfmtIgnore(workingDirectory)

  internal fun loadKtfmtIgnore(startDir: Path): List<String> {
    // Search upward from starting directory to find .ktfmtignore (like .gitignore)
    var currentDir: Path? = startDir.toAbsolutePath()

    while (currentDir != null) {
      val ignoreFile = currentDir.resolve(".ktfmtignore")
      if (Files.exists(ignoreFile)) {
        return Files.readAllLines(ignoreFile).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
      }
      currentDir = currentDir.parent
    }

    return emptyList()
  }

  private fun isUnderWorkingDirectory(filePath: Path): Boolean =
    try {
      val normalizedFile = filePath.toAbsolutePath().normalize()
      val normalizedWorkingDir = workingDirectory.toAbsolutePath().normalize()
      normalizedFile.startsWith(normalizedWorkingDir)
    } catch (e: java.nio.file.InvalidPathException) {
      false // Invalid path syntax
    } catch (e: java.nio.file.FileSystemException) {
      false // File system access error
    }

  internal fun isIgnored(filePath: Path, ignorePatterns: List<String>, workingDir: Path): Boolean {
    if (ignorePatterns.isEmpty()) return false

    val relativePath =
      try {
        workingDir.relativize(filePath)
      } catch (e: IllegalArgumentException) {
        // If files have different roots, use absolute path
        filePath
      }

    // Process patterns sequentially like .gitignore
    var ignored = false

    for (pattern in ignorePatterns) {
      val isNegation = pattern.startsWith("!")
      val actualPattern = if (isNegation) pattern.substring(1) else pattern

      val matcher = fileSystem.getPathMatcher("glob:$actualPattern")
      if (matcher.matches(relativePath)) {
        ignored = !isNegation
      }
    }

    return ignored
  }

  internal fun processWrite(
    files: List<Path>,
    options: FormattingOptions,
    concurrency: Int = Runtime.getRuntime().availableProcessors(),
  ): Int {
    val changed = AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(concurrency)
    try {
      val futures =
        files.map { file ->
          executor.submit<Unit> {
            val originalCode = Files.readString(file)
            val formattedCode = Formatter.format(options, originalCode)
            if (originalCode != formattedCode) {
              Files.writeString(file, formattedCode)
              println("Formatted $file")
              changed.incrementAndGet()
            }
          }
        }
      futures.forEach { it.get() }
    } finally {
      executor.shutdown()
    }
    println("Formatted ${changed.get()} of ${files.size} files")
    return 0
  }

  internal fun processCheck(
    files: List<Path>,
    options: FormattingOptions,
    concurrency: Int = Runtime.getRuntime().availableProcessors(),
  ): Int {
    val needsFormatting = AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(concurrency)
    try {
      val futures =
        files.map { file ->
          executor.submit<Unit> {
            val originalCode = Files.readString(file)
            val formattedCode = Formatter.format(options, originalCode)
            if (originalCode != formattedCode) {
              println("$file needs formatting")
              needsFormatting.incrementAndGet()
            }
          }
        }
      futures.forEach { it.get() }
    } finally {
      executor.shutdown()
    }
    val count = needsFormatting.get()

    return if (count > 0) {
      println("$count of ${files.size} files need formatting")
      1
    } else {
      println("All ${files.size} files are properly formatted")
      0
    }
  }
}

fun main(args: Array<String>) {
  val app = App()
  try {
    val exitCode = app.run(args)
    exitProcess(exitCode)
  } catch (e: KtfmtCliException) {
    System.err.println("Error: ${e.message}")
    exitProcess(1)
  }
}
