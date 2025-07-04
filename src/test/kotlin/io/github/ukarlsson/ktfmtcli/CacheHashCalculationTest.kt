package io.github.ukarlsson.ktfmtcli

import com.facebook.ktfmt.format.FormattingOptions
import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

class CacheHashCalculationTest :
  DescribeSpec({
    describe("MurmurHash cache calculation") {
      it("should calculate deterministic hashes for the same file and options") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create test file with specific content
        val testFile = workingDir.resolve("test.kt")
        val fileContent = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, fileContent.toByteArray())

        // Use the same FormattingOptions as the failing test
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        // Calculate hash twice - should be identical
        val hash1 = cacheManager.calculateHash(testFile, options)
        val hash2 = cacheManager.calculateHash(testFile, options)

        hash1 shouldBe hash2
        println("Hash calculation is deterministic: $hash1 == $hash2")
      }

      it("should calculate different hashes for different options") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val testFile = workingDir.resolve("test.kt")
        val fileContent = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, fileContent.toByteArray())

        val options1 =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val options2 =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 120, // Different max width
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        val hash1 = cacheManager.calculateHash(testFile, options1)
        val hash2 = cacheManager.calculateHash(testFile, options2)

        hash1 shouldNotBe hash2
        println("Different options produce different hashes: $hash1 != $hash2")
      }

      it("should calculate different hashes for different file contents") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val testFile1 = workingDir.resolve("test1.kt")
        val testFile2 = workingDir.resolve("test2.kt")
        Files.write(testFile1, "fun main() {\n  println(\"hello\")\n}\n".toByteArray())
        Files.write(testFile2, "fun main() {\n  println(\"world\")\n}\n".toByteArray())

        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        val hash1 = cacheManager.calculateHash(testFile1, options)
        val hash2 = cacheManager.calculateHash(testFile2, options)

        hash1 shouldNotBe hash2
        println("Different file contents produce different hashes: $hash1 != $hash2")
      }

      it("should preserve hash when saving and loading cache") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val testFile = workingDir.resolve("test.kt")
        val fileContent = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, fileContent.toByteArray())

        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        // Calculate original hash
        val originalHash = cacheManager.calculateHash(testFile, options)
        println("Original hash: $originalHash")

        // Save to cache
        val normalizedPath = testFile.toAbsolutePath().normalize()
        val cacheData = mapOf(normalizedPath to originalHash)
        cacheManager.saveCache(cacheData)

        // Load from cache
        val loadedCache = cacheManager.loadCache()
        val loadedHash = loadedCache[normalizedPath]

        loadedHash shouldBe originalHash
        println("Loaded hash matches original: $loadedHash == $originalHash")

        // Verify cache file contents
        val cacheFileContent = Files.readString(cacheFile).trim()
        println("Cache file content: $cacheFileContent")
        cacheFileContent shouldBe "$originalHash,$normalizedPath"
      }

      it("should test shouldSkipFile logic with same file and options") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val testFile = workingDir.resolve("test.kt")
        val fileContent = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, fileContent.toByteArray())

        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        // Calculate hash and create cache
        val hash = cacheManager.calculateHash(testFile, options)
        val normalizedPath = testFile.toAbsolutePath().normalize()
        val cache = mapOf(normalizedPath to hash)

        println("Testing shouldSkipFile with:")
        println("  File: $normalizedPath")
        println("  Hash: $hash")
        println("  Options: $options")

        // Should skip file when hash matches
        val shouldSkip = cacheManager.shouldSkipFile(testFile, cache, options)
        shouldSkip shouldBe true
        println("Should skip file: $shouldSkip")

        // Should not skip file when hash doesn't match (different options)
        val differentOptions = options.copy(maxWidth = 120)
        val shouldNotSkip = cacheManager.shouldSkipFile(testFile, cache, differentOptions)
        shouldNotSkip shouldBe false
        println("Should not skip with different options: $shouldNotSkip")
      }

      it("should debug FormattingOptions toString() stability") {
        val options1 =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val options2 =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val toString1 = options1.toString()
        val toString2 = options2.toString()

        println("FormattingOptions toString() comparison:")
        println("  options1.toString(): $toString1")
        println("  options2.toString(): $toString2")
        println("  Are equal: ${toString1 == toString2}")

        toString1 shouldBe toString2
      }

      it("should reproduce the failing test scenario exactly") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create the exact same file content as in the failing test
        val cachedFile = workingDir.resolve("cached.kt")
        val formattedCode = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(cachedFile, formattedCode.toByteArray())

        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        val cacheFile = workingDir.resolve(".ktfmt-cache")
        val cacheManager = CacheManager(cacheFile)

        // Pre-populate cache exactly like the failing test
        val initialCache =
          mapOf(cachedFile.toAbsolutePath().normalize() to cacheManager.calculateHash(cachedFile, options))
        cacheManager.saveCache(initialCache)

        println("Debug information:")
        println("  File path: ${cachedFile.toAbsolutePath().normalize()}")
        println("  File content: ${Files.readString(cachedFile).replace("\n", "\\n")}")
        println("  Options: $options")
        println("  Calculated hash: ${cacheManager.calculateHash(cachedFile, options)}")
        println("  Cache content: ${Files.readString(cacheFile)}")

        // Load cache and verify it matches
        val loadedCache = cacheManager.loadCache()
        val normalizedPath = cachedFile.toAbsolutePath().normalize()
        val cachedHash = loadedCache[normalizedPath]
        val currentHash = cacheManager.calculateHash(cachedFile, options)

        println("  Loaded cache hash: $cachedHash")
        println("  Current hash: $currentHash")
        println("  Hashes match: ${cachedHash == currentHash}")

        // Test shouldSkipFile
        val shouldSkip = cacheManager.shouldSkipFile(cachedFile, loadedCache, options)
        println("  Should skip file: $shouldSkip")

        cachedHash shouldBe currentHash
        shouldSkip shouldBe true
      }
    }
  })
