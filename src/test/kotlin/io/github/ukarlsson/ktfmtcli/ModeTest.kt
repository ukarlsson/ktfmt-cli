package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ModeTest :
  DescribeSpec({
    describe("App mode validation with jimfs") {
      it("should validate that exactly one mode is specified") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test runFormatting directly - no modes specified should return 1
        val exitCode =
          app.runFormatting(
            globs = listOf("**/*"),
            style = "meta",
            maxWidth = 100,
            blockIndent = null,
            continuationIndent = null,
            removeUnusedImports = true,
            manageTrailingCommas = null,
            write = false,
            check = false,
            debug = false,
            concurrency = 1,
            cacheLocation = null,
          )
        exitCode shouldBe 1
      }

      it("should reject multiple modes") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test runFormatting directly - multiple modes specified should return 1
        val exitCode =
          app.runFormatting(
            globs = listOf("**/*"),
            style = "meta",
            maxWidth = 100,
            blockIndent = null,
            continuationIndent = null,
            removeUnusedImports = true,
            manageTrailingCommas = null,
            write = true,
            check = true,
            debug = false,
            concurrency = 1,
            cacheLocation = null,
          )
        exitCode shouldBe 1
      }

      it("should accept single mode flag") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test runFormatting directly - single mode should return 0
        val exitCode =
          app.runFormatting(
            globs = listOf("**/*"),
            style = "meta",
            maxWidth = 100,
            blockIndent = null,
            continuationIndent = null,
            removeUnusedImports = true,
            manageTrailingCommas = null,
            write = true,
            check = false,
            debug = false,
            concurrency = 1,
            cacheLocation = null,
          )
        exitCode shouldBe 0
      }
    }

    describe("file processing modes with jimfs") {
      it("should detect files that need formatting in check mode") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create a test file with unformatted code
        val testFile = workingDir.resolve("test.kt")
        val unformattedCode = "class Test{val x=1}"
        Files.write(testFile, unformattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = listOf(testFile)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        val result = app.processCheck(files, formattingOptions)

        // Should have files needing formatting
        result.filesNeedingFormatting.size shouldBe 1
      }

      it("should handle empty file lists") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        app.processCheck(emptyList(), formattingOptions).allFiles.size shouldBe 0
        app.processWrite(emptyList(), formattingOptions).allFiles.size shouldBe 0
      }

      it("should handle write mode with properly formatted files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create a test file with already formatted code (2-space indent to match FormattingOptions)
        val testFile = workingDir.resolve("test.kt")
        val formattedCode = "class Test {\n  val x = 1\n}\n"
        Files.write(testFile, formattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = listOf(testFile)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        val result = app.processWrite(files, formattingOptions)

        // Should process file but not format it (already formatted)
        result.allFiles.size shouldBe 1
        result.formattedFiles.size shouldBe 0
      }

      it("should handle multiple files with mixed formatting states") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create files with different formatting states
        val unformattedFile = workingDir.resolve("unformatted.kt")
        val formattedFile = workingDir.resolve("formatted.kt")

        Files.write(unformattedFile, "class Unformatted{val x=1}".toByteArray())
        Files.write(formattedFile, "class Formatted {\n  val x = 1\n}\n".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = listOf(unformattedFile, formattedFile)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        // Check mode should detect at least one file needs formatting
        val result = app.processCheck(files, formattingOptions)
        result.filesNeedingFormatting.size shouldBe 1
      }
    }

    describe("integration scenarios with jimfs") {
      it("should respect ignore patterns during file collection and processing") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        val buildDir = workingDir.resolve("build")
        Files.createDirectories(srcDir)
        Files.createDirectories(buildDir)

        // Create files
        Files.write(srcDir.resolve("App.kt"), "class App{val x=1}".toByteArray())
        Files.write(buildDir.resolve("Generated.kt"), "class Generated{val x=1}".toByteArray())

        // Create ignore file
        Files.write(workingDir.resolve(".ktfmtignore"), "build/**".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test with runFormatting directly to avoid exitProcess issues
        val exitCode =
          app.runFormatting(
            globs = listOf("**/*"),
            style = "meta",
            maxWidth = 100,
            blockIndent = null,
            continuationIndent = null,
            removeUnusedImports = true,
            manageTrailingCommas = null,
            write = false,
            check = true,
            debug = false,
            concurrency = 1,
            cacheLocation = null,
          )

        // Should only process src files, not build files
        // Since src file needs formatting, should exit with 1
        exitCode shouldBe 1
      }

      it("should handle project with no Kotlin files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create non-Kotlin files
        Files.write(workingDir.resolve("README.md"), "# Project".toByteArray())
        Files.write(workingDir.resolve("build.gradle"), "plugins { }".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test with runFormatting directly to avoid exitProcess issues
        val exitCode =
          app.runFormatting(
            globs = listOf("**/*"),
            style = "meta",
            maxWidth = 100,
            blockIndent = null,
            continuationIndent = null,
            removeUnusedImports = true,
            manageTrailingCommas = null,
            write = false,
            check = true,
            debug = false,
            concurrency = 1,
            cacheLocation = null,
          )

        // Should exit with 0 when no matching files found
        exitCode shouldBe 0
      }
    }
  })
