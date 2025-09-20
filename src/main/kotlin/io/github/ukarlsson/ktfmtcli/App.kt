package io.github.ukarlsson.ktfmtcli

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import com.facebook.ktfmt.format.TrailingCommaManagementStrategy
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
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
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

class KtfmtCliException(message: String) : Exception(message)

sealed interface FormattingResult {
  val allFiles: List<Path>
  val skippedFiles: List<Path>
  val cacheUpdates: Map<Path, Int>

  data class WriteResult(
    override val allFiles: List<Path>,
    val formattedFiles: List<Path>,
    override val skippedFiles: List<Path>,
    val failedFiles: List<Path>,
    override val cacheUpdates: Map<Path, Int>,
  ) : FormattingResult

  data class CheckResult(
    override val allFiles: List<Path>,
    val filesNeedingFormatting: List<Path>,
    override val skippedFiles: List<Path>,
    val failedFiles: List<Path>,
    override val cacheUpdates: Map<Path, Int>,
  ) : FormattingResult
}

class App(
  private val fileSystem: FileSystem = FileSystems.getDefault(),
  private val workingDirectory: Path = Paths.get("").toAbsolutePath(),
) : CliktCommand(name = "ktfmt-cli", help = "A command-line interface for ktfmt, the Kotlin code formatter") {

  // Command line options
  private val style by
    option("--style")
      .choice("meta", "google", "kotlinlang")
      .default("meta")
      .help("Formatting style: meta (default), google, or kotlinlang")

  private val maxWidth by option("--max-width", "-w").int().default(100).help("Maximum line width")

  private val blockIndent by
    option("--block-indent").int().help("Block indent size in spaces (overrides style default)")

  private val continuationIndent by
    option("--continuation-indent").int().help("Continuation indent size in spaces (overrides style default)")

  private val removeUnusedImports by
    option("--remove-unused-imports").flag("--no-remove-unused-imports", default = true).help("Remove unused imports")

  private val manageTrailingCommas by
    option("--manage-trailing-commas")
      .switch("--manage-trailing-commas" to true, "--no-manage-trailing-commas" to false)
      .help("Automatically add/remove trailing commas (defaults based on style)")

  private val write by option("--write").flag().help("Write formatting changes to files")

  private val check by option("--check").flag().help("Check if files need formatting, exit 1 if so")

  private val std by option("--std").flag().help("Read from stdin and write to stdout")

  private val version by option("--version", "-V").flag().help("Print version information")

  private val debug by option("--debug").flag().help("Enable debug output with timing information")

  private val concurrency by
    option("--concurrency", "-j")
      .int()
      .default(Runtime.getRuntime().availableProcessors())
      .help("Number of parallel threads for formatting")

  private val cacheLocation by option("--cache-location").help("Cache file location to skip unchanged files")

  private val globs by
    argument(name = "globs", help = "Files, directories, or glob patterns to format (not used with --std)").multiple()

  private fun formatWriteOutput(result: FormattingResult.WriteResult): String {
    val output = StringBuilder()

    // Report failed files first
    if (result.failedFiles.isNotEmpty()) {
      result.failedFiles.forEach { file -> output.appendLine("Failed to format $file") }
    }

    // Then add summary
    val summary =
      when {
        result.skippedFiles.isNotEmpty() && result.failedFiles.isNotEmpty() -> {
          "Formatted ${result.formattedFiles.size} of ${result.allFiles.size} files (skipped ${result.skippedFiles.size} unchanged, ${result.failedFiles.size} failed)"
        }
        result.skippedFiles.isNotEmpty() -> {
          "Formatted ${result.formattedFiles.size} of ${result.allFiles.size} files (skipped ${result.skippedFiles.size} unchanged)"
        }
        result.failedFiles.isNotEmpty() -> {
          "Formatted ${result.formattedFiles.size} of ${result.allFiles.size} files (${result.failedFiles.size} failed)"
        }
        else -> {
          "Formatted ${result.formattedFiles.size} of ${result.allFiles.size} files"
        }
      }

    output.append(summary)
    return output.toString()
  }

  private fun formatCheckOutput(result: FormattingResult.CheckResult): String {
    val output = StringBuilder()

    // Report failed files first
    if (result.failedFiles.isNotEmpty()) {
      result.failedFiles.forEach { file -> output.appendLine("Failed to check $file") }
    }

    // Then report files that need formatting
    if (result.filesNeedingFormatting.isNotEmpty()) {
      result.filesNeedingFormatting.forEach { file -> output.appendLine("$file needs formatting") }
    }

    // Then add summary
    val summary =
      when {
        result.filesNeedingFormatting.isNotEmpty() -> {
          when {
            result.skippedFiles.isNotEmpty() && result.failedFiles.isNotEmpty() -> {
              "${result.filesNeedingFormatting.size} of ${result.allFiles.size} files need formatting (skipped ${result.skippedFiles.size} unchanged, ${result.failedFiles.size} failed)"
            }
            result.skippedFiles.isNotEmpty() -> {
              "${result.filesNeedingFormatting.size} of ${result.allFiles.size} files need formatting (skipped ${result.skippedFiles.size} unchanged)"
            }
            result.failedFiles.isNotEmpty() -> {
              "${result.filesNeedingFormatting.size} of ${result.allFiles.size} files need formatting (${result.failedFiles.size} failed)"
            }
            else -> {
              "${result.filesNeedingFormatting.size} of ${result.allFiles.size} files need formatting"
            }
          }
        }
        else -> {
          when {
            result.skippedFiles.isNotEmpty() && result.failedFiles.isNotEmpty() -> {
              "All ${result.allFiles.size} files are properly formatted (skipped ${result.skippedFiles.size} unchanged, ${result.failedFiles.size} failed)"
            }
            result.skippedFiles.isNotEmpty() -> {
              "All ${result.allFiles.size} files are properly formatted (skipped ${result.skippedFiles.size} unchanged)"
            }
            result.failedFiles.isNotEmpty() -> {
              "All ${result.allFiles.size} files are properly formatted (${result.failedFiles.size} failed)"
            }
            else -> {
              "All ${result.allFiles.size} files are properly formatted"
            }
          }
        }
      }

    output.append(summary)
    return output.toString()
  }

  override fun run() {
    // Debug output: show parsed options and globs
    if (debug) {
      println("Debug: Parsed options:")
      println("  --style: $style")
      println("  --max-width: $maxWidth")
      println("  --block-indent: $blockIndent")
      println("  --continuation-indent: $continuationIndent")
      println("  --remove-unused-imports: $removeUnusedImports")
      println("  --manage-trailing-commas: $manageTrailingCommas")
      println("  --write: $write")
      println("  --check: $check")
      println("  --std: $std")
      println("  --debug: $debug")
      println("  --concurrency: $concurrency")
      println("  --cache-location: $cacheLocation")
      println("  raw globs: ${(globs as List<String>).joinToString(", ")}")
    }

    // Check if any globs look like options (start with -)
    val suspiciousGlobs = (globs as List<String>).filter { it.startsWith("-") }
    if (suspiciousGlobs.isNotEmpty()) {
      System.err.println("Error: Unknown option(s): ${suspiciousGlobs.joinToString(", ")}")
      exitProcess(1)
    }

    if (version) {
      printVersion()
      return
    }

    // Validate that globs are not provided with --std mode
    if (std && (globs as List<String>).isNotEmpty()) {
      System.err.println("Error: Cannot specify files/patterns when using --std mode")
      exitProcess(1)
    }

    val actualGlobs = if ((globs as List<String>).isEmpty()) listOf("**/*") else globs

    if (debug) {
      println("Debug: Effective globs: ${(actualGlobs as List<String>).joinToString(", ")}")
    }

    val exitCode =
      runFormatting(
        actualGlobs,
        style,
        maxWidth,
        blockIndent,
        continuationIndent,
        removeUnusedImports,
        manageTrailingCommas,
        write,
        check,
        std,
        debug,
        concurrency,
        cacheLocation,
      )

    if (exitCode != 0) {
      exitProcess(exitCode)
    }
  }

  internal fun runFormatting(
    globs: List<String>,
    style: String,
    maxWidth: Int,
    blockIndent: Int?,
    continuationIndent: Int?,
    removeUnusedImports: Boolean,
    manageTrailingCommas: Boolean?,
    write: Boolean,
    check: Boolean,
    std: Boolean,
    debug: Boolean,
    concurrency: Int,
    cacheLocation: String?,
  ): Int {
    // Validate flags
    val modeCount = listOf(write, check, std).count { it }
    if (modeCount == 0) {
      System.err.println("Error: Must specify one of --write, --check, or --std")
      return 1
    }
    if (modeCount > 1) {
      System.err.println("Error: Cannot specify multiple modes (--write, --check, --std)")
      return 1
    }

    // Skip file collection for stdin mode
    val (files, collectionTime) =
      if (std) {
        emptyList<Path>() to kotlin.time.Duration.ZERO
      } else {
        val timedValue = measureTimedValue { collectFiles(workingDirectory, globs, debug) }
        timedValue.value to timedValue.duration
      }

    if (debug && !std) {
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
        maxWidth = maxWidth,
        blockIndent = blockIndent ?: defaultBlockIndent,
        continuationIndent = continuationIndent ?: defaultContinuationIndent,
        trailingCommaManagementStrategy =
          if (manageTrailingCommas ?: defaultTrailingCommas) TrailingCommaManagementStrategy.COMPLETE
          else TrailingCommaManagementStrategy.NONE,
        removeUnusedImports = removeUnusedImports,
      )

    return when {
      write -> {
        val (result, formatTime) =
          measureTimedValue { processWrite(files, formattingOptions, concurrency, cacheLocation) }
        if (debug) {
          println("Debug: Formatting took $formatTime")
        }
        println(formatWriteOutput(result))
        if (result.failedFiles.isNotEmpty()) 1 else 0
      }
      check -> {
        val (result, formatTime) =
          measureTimedValue { processCheck(files, formattingOptions, concurrency, cacheLocation) }
        if (debug) {
          println("Debug: Checking took $formatTime")
        }
        println(formatCheckOutput(result))
        if (result.filesNeedingFormatting.isNotEmpty() || result.failedFiles.isNotEmpty()) 1 else 0
      }
      std -> {
        val (exitCode, formatTime) = measureTimedValue { processStdin(formattingOptions) }
        if (debug) {
          System.err.println("Debug: Formatting took $formatTime")
        }
        exitCode
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

  private fun collectFromPath(
    path: Path,
    originalGlob: String,
    ignorePatterns: List<String>,
    ignoreBaseDir: Path,
  ): List<Path> {
    if (!isUnderWorkingDirectory(path)) {
      if (Files.isDirectory(path)) {
        throw KtfmtCliException("Directory '$originalGlob' is outside the working directory")
      } else {
        throw KtfmtCliException("File '$originalGlob' is outside the working directory")
      }
    }

    return when {
      Files.isRegularFile(path) -> {
        if (isValidKotlinFile(path, ignorePatterns, ignoreBaseDir)) listOf(path) else emptyList()
      }
      Files.isDirectory(path) -> {
        val results = mutableListOf<Path>()
        Files.walkFileTree(
          path,
          object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
              // Skip the starting directory check, but check all subdirectories
              if (dir != path && isIgnored(dir, ignorePatterns, ignoreBaseDir)) {
                return FileVisitResult.SKIP_SUBTREE
              }
              // Security check: ensure directory is under working directory
              if (!isUnderWorkingDirectory(dir)) {
                return FileVisitResult.SKIP_SUBTREE
              }
              return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
              if (isValidKotlinFile(file, ignorePatterns, ignoreBaseDir)) {
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
    ignoreBaseDir: Path,
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
            val ignored = isIgnored(dir, ignorePatterns, ignoreBaseDir)
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
                !isIgnored(file, ignorePatterns, ignoreBaseDir)
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
    val (ignoreResult, ignoreTime) = measureTimedValue { loadKtfmtIgnore() }
    val ignorePatterns = ignoreResult.patterns
    val ignoreBaseDir = ignoreResult.baseDirectory
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
                // Check if the resolved path itself is ignored
                if (isIgnored(resolvedPath, ignorePatterns, ignoreBaseDir)) {
                  emptyList()
                } else {
                  collectFromPath(resolvedPath, glob, ignorePatterns, ignoreBaseDir)
                }
              }
              else -> {
                // Path doesn't exist, treat as glob pattern
                collectFromGlob(glob, startingDir, ignorePatterns, ignoreBaseDir, debug)
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

  data class KtfmtIgnoreResult(val patterns: List<String>, val baseDirectory: Path)

  internal fun loadKtfmtIgnore(): KtfmtIgnoreResult = loadKtfmtIgnore(workingDirectory)

  internal fun loadKtfmtIgnore(startDir: Path): KtfmtIgnoreResult {
    // Search upward from starting directory to find .ktfmtignore (like .gitignore)
    var currentDir: Path? = startDir.toAbsolutePath()

    while (currentDir != null) {
      val ignoreFile = currentDir.resolve(".ktfmtignore")
      if (Files.exists(ignoreFile)) {
        val patterns =
          Files.readAllLines(ignoreFile).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        return KtfmtIgnoreResult(patterns, currentDir)
      }
      currentDir = currentDir.parent
    }

    return KtfmtIgnoreResult(emptyList(), startDir)
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

    val normalizedFile = filePath.toAbsolutePath().normalize()
    val normalizedWorkingDir = workingDir.toAbsolutePath().normalize()

    val relativePath =
      try {
        normalizedWorkingDir.relativize(normalizedFile)
      } catch (e: IllegalArgumentException) {
        // If files have different roots, use absolute path
        normalizedFile
      }

    // Process patterns sequentially like .gitignore
    var ignored = false

    for (pattern in ignorePatterns) {
      val isNegation = pattern.startsWith("!")
      val actualPattern = if (isNegation) pattern.substring(1) else pattern

      val matcher = fileSystem.getPathMatcher("glob:$actualPattern")

      // Check if the file itself matches
      if (matcher.matches(relativePath)) {
        ignored = !isNegation
      } else {
        // Check if any parent directory matches (like .gitignore behavior)
        var currentPath: Path? = relativePath.parent
        while (currentPath != null) {
          if (matcher.matches(currentPath)) {
            ignored = !isNegation
            break
          }
          currentPath = currentPath.parent
        }
      }
    }

    return ignored
  }

  internal fun processWrite(
    files: List<Path>,
    options: FormattingOptions,
    concurrency: Int = Runtime.getRuntime().availableProcessors(),
    cacheLocation: String? = null,
  ): FormattingResult.WriteResult {
    val cacheManager = cacheLocation?.let { CacheManager(fileSystem.getPath(it)) }
    val cache = cacheManager?.loadCache() ?: emptyMap()
    val updatedCache = cache.toMutableMap()
    val formattedFiles = mutableListOf<Path>()
    val skippedFiles = mutableListOf<Path>()
    val failedFiles = mutableListOf<Path>()
    val executor = Executors.newFixedThreadPool(concurrency)

    try {
      val futures =
        files.map { file ->
          executor.submit<Unit> {
            // Check cache first
            if (cacheManager != null && cacheManager.shouldSkipFile(file, cache, options)) {
              synchronized(skippedFiles) { skippedFiles.add(file) }
              return@submit
            }

            try {
              val originalCode = Files.readString(file)
              val formattedCode = Formatter.format(options, originalCode)
              if (originalCode != formattedCode) {
                Files.writeString(file, formattedCode)
                synchronized(formattedFiles) { formattedFiles.add(file) }
              }

              // Update cache with current hash
              if (cacheManager != null) {
                synchronized(updatedCache) {
                  updatedCache[file.toAbsolutePath().normalize()] = cacheManager.calculateHash(file, options)
                }
              }
            } catch (e: com.facebook.ktfmt.format.ParseError) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to parse $file - ${e.message}")
            } catch (e: java.lang.RuntimeException) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to format $file - ${e.message}")
            } catch (e: java.io.IOException) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to read/write $file - ${e.message}")
            }
          }
        }
      futures.forEach { it.get() }
    } finally {
      executor.shutdown()
    }

    // Save updated cache
    if (cacheManager != null) {
      cacheManager.saveCache(updatedCache)
    }

    return FormattingResult.WriteResult(
      allFiles = files,
      formattedFiles = formattedFiles.toList(),
      skippedFiles = skippedFiles.toList(),
      failedFiles = failedFiles.toList(),
      cacheUpdates = updatedCache,
    )
  }

  internal fun processStdin(options: FormattingOptions): Int =
    try {
      val input = System.`in`.bufferedReader().use { it.readText() }
      val formatted = Formatter.format(options, input)
      print(formatted)
      0
    } catch (e: com.facebook.ktfmt.format.ParseError) {
      System.err.println("Error: Failed to parse input - ${e.message}")
      1
    } catch (e: java.lang.RuntimeException) {
      System.err.println("Error: Failed to format input - ${e.message}")
      1
    } catch (e: java.io.IOException) {
      System.err.println("Error: Failed to read from stdin - ${e.message}")
      1
    }

  internal fun processCheck(
    files: List<Path>,
    options: FormattingOptions,
    concurrency: Int = Runtime.getRuntime().availableProcessors(),
    cacheLocation: String? = null,
  ): FormattingResult.CheckResult {
    val cacheManager = cacheLocation?.let { CacheManager(fileSystem.getPath(it)) }
    val cache = cacheManager?.loadCache() ?: emptyMap()
    val updatedCache = cache.toMutableMap()
    val filesNeedingFormatting = mutableListOf<Path>()
    val skippedFiles = mutableListOf<Path>()
    val failedFiles = mutableListOf<Path>()
    val executor = Executors.newFixedThreadPool(concurrency)

    try {
      val futures =
        files.map { file ->
          executor.submit<Unit> {
            // Check cache first
            if (cacheManager != null && cacheManager.shouldSkipFile(file, cache, options)) {
              synchronized(skippedFiles) { skippedFiles.add(file) }
              return@submit
            }

            try {
              val originalCode = Files.readString(file)
              val formattedCode = Formatter.format(options, originalCode)
              if (originalCode != formattedCode) {
                synchronized(filesNeedingFormatting) { filesNeedingFormatting.add(file) }
              } else {
                // Only update cache if file is already properly formatted
                if (cacheManager != null) {
                  synchronized(updatedCache) {
                    updatedCache[file.toAbsolutePath().normalize()] = cacheManager.calculateHash(file, options)
                  }
                }
              }
            } catch (e: com.facebook.ktfmt.format.ParseError) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to parse $file - ${e.message}")
            } catch (e: java.lang.RuntimeException) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to format $file - ${e.message}")
            } catch (e: java.io.IOException) {
              synchronized(failedFiles) { failedFiles.add(file) }
              System.err.println("Error: Failed to read/write $file - ${e.message}")
            }
          }
        }
      futures.forEach { it.get() }
    } finally {
      executor.shutdown()
    }

    // Save updated cache
    if (cacheManager != null) {
      cacheManager.saveCache(updatedCache)
    }

    return FormattingResult.CheckResult(
      allFiles = files,
      filesNeedingFormatting = filesNeedingFormatting.toList(),
      skippedFiles = skippedFiles.toList(),
      failedFiles = failedFiles.toList(),
      cacheUpdates = updatedCache,
    )
  }
}

fun main(args: Array<String>) {
  val app = App()
  try {
    app.main(args)
  } catch (e: KtfmtCliException) {
    System.err.println("Error: ${e.message}")
    exitProcess(1)
  }
}
