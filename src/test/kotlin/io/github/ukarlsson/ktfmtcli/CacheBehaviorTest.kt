package io.github.ukarlsson.ktfmtcli

import com.facebook.ktfmt.format.FormattingOptions
import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class CacheBehaviorTest :
  DescribeSpec({
    describe("cache behavior in check vs write mode") {
      it("should NOT update cache for unformatted files in check mode") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val cacheFile = workingDir.resolve(".ktfmt-cache")
        Files.createDirectories(workingDir)

        // Create a file that needs formatting
        val testFile = workingDir.resolve("test.kt")
        val unformattedCode = "fun    main(   ){   println(\"hello\")   }"
        Files.write(testFile, unformattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        // Run check mode - should NOT update cache for unformatted file
        val result = app.processCheck(listOf(testFile), options, cacheLocation = cacheFile.toString())

        // Verify the result indicates file needs formatting
        result.allFiles shouldContainExactlyInAnyOrder listOf(testFile)
        result.filesNeedingFormatting shouldContainExactlyInAnyOrder listOf(testFile)
        result.skippedFiles shouldContainExactlyInAnyOrder emptyList()

        // Verify cache is empty (unformatted file should not be cached)
        result.cacheUpdates shouldBe emptyMap()
      }

      it("should update cache for properly formatted files in check mode") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val cacheFile = workingDir.resolve(".ktfmt-cache")
        Files.createDirectories(workingDir)

        // Create a properly formatted file
        val testFile = workingDir.resolve("test.kt")
        val formattedCode = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, formattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        // Run check mode - should update cache for properly formatted file
        val result = app.processCheck(listOf(testFile), options, cacheLocation = cacheFile.toString())

        // Verify the result indicates no files need formatting
        result.allFiles shouldContainExactlyInAnyOrder listOf(testFile)
        result.filesNeedingFormatting shouldContainExactlyInAnyOrder emptyList()
        result.skippedFiles shouldContainExactlyInAnyOrder emptyList()

        // Verify cache contains the formatted file
        result.cacheUpdates.shouldContainKey(testFile.toAbsolutePath().normalize())
      }

      it("should always update cache after formatting in write mode") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val cacheFile = workingDir.resolve(".ktfmt-cache")
        Files.createDirectories(workingDir)

        // Create a file that needs formatting
        val testFile = workingDir.resolve("test.kt")
        val unformattedCode = "fun    main(   ){   println(\"hello\")   }"
        Files.write(testFile, unformattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        // Run write mode - should format file and update cache
        val result = app.processWrite(listOf(testFile), options, cacheLocation = cacheFile.toString())

        // Verify the result indicates file was formatted
        result.allFiles shouldContainExactlyInAnyOrder listOf(testFile)
        result.formattedFiles shouldContainExactlyInAnyOrder listOf(testFile)
        result.skippedFiles shouldContainExactlyInAnyOrder emptyList()

        // Verify file was actually formatted
        val fileContent = Files.readString(testFile)
        fileContent shouldBe "fun main() {\n  println(\"hello\")\n}\n"

        // Verify cache contains the formatted file hash
        result.cacheUpdates.shouldContainKey(testFile.toAbsolutePath().normalize())
      }

      it("should handle mixed scenarios with cache correctly") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val cacheFile = workingDir.resolve(".ktfmt-cache")
        Files.createDirectories(workingDir)

        // Create multiple files: formatted, unformatted, and one to be skipped by cache
        val formattedFile = workingDir.resolve("formatted.kt")
        val unformattedFile = workingDir.resolve("unformatted.kt")
        val cachedFile = workingDir.resolve("cached.kt")

        val formattedCode = "fun main() {\n  println(\"hello\")\n}\n"
        val unformattedCode = "fun    main(   ){   println(\"hello\")   }"

        Files.write(formattedFile, formattedCode.toByteArray())
        Files.write(unformattedFile, unformattedCode.toByteArray())
        Files.write(cachedFile, formattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        // Pre-populate cache with cachedFile
        val cacheManager = CacheManager(cacheFile)
        val initialCache =
          mapOf(cachedFile.toAbsolutePath().normalize() to cacheManager.calculateHash(cachedFile, options))
        cacheManager.saveCache(initialCache)

        // Run check mode on all files
        val checkResult =
          app.processCheck(
            listOf(formattedFile, unformattedFile, cachedFile),
            options,
            cacheLocation = cacheFile.toString(),
          )

        // Verify results
        checkResult.allFiles shouldContainExactlyInAnyOrder listOf(formattedFile, unformattedFile, cachedFile)
        checkResult.filesNeedingFormatting shouldContainExactlyInAnyOrder listOf(unformattedFile)
        checkResult.skippedFiles shouldContainExactlyInAnyOrder listOf(cachedFile) // cachedFile should be skipped

        // Verify cache updates: only formattedFile should be added (unformattedFile should NOT be
        // cached)
        checkResult.cacheUpdates.shouldContainKey(formattedFile.toAbsolutePath().normalize())
        checkResult.cacheUpdates.shouldContainKey(cachedFile.toAbsolutePath().normalize()) // pre-existing
        checkResult.cacheUpdates.shouldNotContainKey(unformattedFile.toAbsolutePath().normalize())

        // Now run write mode on the unformatted file
        val writeResult = app.processWrite(listOf(unformattedFile), options, cacheLocation = cacheFile.toString())

        // Verify write results
        writeResult.allFiles shouldContainExactlyInAnyOrder listOf(unformattedFile)
        writeResult.formattedFiles shouldContainExactlyInAnyOrder listOf(unformattedFile)
        writeResult.skippedFiles shouldContainExactlyInAnyOrder emptyList()

        // Verify the unformatted file is now in cache after being formatted
        writeResult.cacheUpdates.shouldContainKey(unformattedFile.toAbsolutePath().normalize())

        // Run check mode again - all files should now be cached/formatted
        val finalCheckResult =
          app.processCheck(
            listOf(formattedFile, unformattedFile, cachedFile),
            options,
            cacheLocation = cacheFile.toString(),
          )

        finalCheckResult.allFiles shouldContainExactlyInAnyOrder listOf(formattedFile, unformattedFile, cachedFile)
        finalCheckResult.filesNeedingFormatting shouldContainExactlyInAnyOrder emptyList()
        finalCheckResult.skippedFiles shouldContainExactlyInAnyOrder
          listOf(formattedFile, unformattedFile, cachedFile) // All files should now be skipped due to cache
      }

      it("should not cache files when no cache location specified") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val testFile = workingDir.resolve("test.kt")
        val formattedCode = "fun main() {\n  println(\"hello\")\n}\n"
        Files.write(testFile, formattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val options =
          FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            maxWidth = 100,
            removeUnusedImports = true,
            manageTrailingCommas = false,
          )

        // Run without cache location
        val checkResult = app.processCheck(listOf(testFile), options)
        val writeResult = app.processWrite(listOf(testFile), options)

        // Both should have empty cache updates
        checkResult.cacheUpdates shouldBe emptyMap()
        writeResult.cacheUpdates shouldBe emptyMap()
      }
    }
  })
