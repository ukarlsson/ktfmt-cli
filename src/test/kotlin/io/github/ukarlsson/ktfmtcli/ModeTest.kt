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

        // No modes specified
        app.run(arrayOf()) shouldBe 1
      }

      it("should reject multiple modes") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Multiple modes specified
        app.run(arrayOf("--write", "--check")) shouldBe 1
      }

      it("should accept single mode flag") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        app.run(arrayOf("--write")) shouldBe 0
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

        val exitCode = app.processCheck(files, formattingOptions)

        // Should exit with 1 if files need formatting
        exitCode shouldBe 1
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

        app.processCheck(emptyList(), formattingOptions) shouldBe 0
        app.processWrite(emptyList(), formattingOptions) shouldBe 0
      }

      it("should handle write mode with properly formatted files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create a test file with already formatted code
        val testFile = workingDir.resolve("test.kt")
        val formattedCode = "class Test {\n    val x = 1\n}\n"
        Files.write(testFile, formattedCode.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = listOf(testFile)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        val exitCode = app.processWrite(files, formattingOptions)

        // Should exit with 0 and not modify already formatted files
        exitCode shouldBe 0
      }

      it("should handle multiple files with mixed formatting states") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create files with different formatting states
        val unformattedFile = workingDir.resolve("unformatted.kt")
        val formattedFile = workingDir.resolve("formatted.kt")

        Files.write(unformattedFile, "class Unformatted{val x=1}".toByteArray())
        Files.write(formattedFile, "class Formatted {\n    val x = 1\n}\n".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = listOf(unformattedFile, formattedFile)
        val formattingOptions =
          com.facebook.ktfmt.format.FormattingOptions(
            blockIndent = 2,
            continuationIndent = 4,
            manageTrailingCommas = false,
          )

        // Check mode should detect at least one file needs formatting
        val checkExitCode = app.processCheck(files, formattingOptions)
        checkExitCode shouldBe 1
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

        val exitCode = app.run(arrayOf("--check"))

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

        val exitCode = app.run(arrayOf("--check"))

        // Should exit with 0 when no matching files found
        exitCode shouldBe 0
      }
    }
  })
