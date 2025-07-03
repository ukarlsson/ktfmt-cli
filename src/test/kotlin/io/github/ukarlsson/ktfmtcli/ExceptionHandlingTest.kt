package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ExceptionHandlingTest :
  DescribeSpec({
    describe("Exception handling with jimfs") {
      it("should throw KtfmtCliException for files outside working directory") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val outsideDir = fs.getPath("/outside")
        Files.createDirectories(workingDir)
        Files.createDirectories(outsideDir)

        // Create a file outside the working directory
        val outsideFile = outsideDir.resolve("App.kt")
        Files.write(outsideFile, "class App".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val exception = shouldThrow<KtfmtCliException> { app.collectFiles(workingDir, listOf("../outside/App.kt")) }

        exception.message shouldBe "File '../outside/App.kt' is outside the working directory"
      }

      it("should return empty list for non-existent files (treated as glob patterns)") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val files = app.collectFiles(workingDir, listOf("nonexistent.kt"))

        files shouldBe emptyList()
      }

      it("should return empty list for non-existent paths (treated as glob patterns)") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Non-existent paths are now treated as glob patterns and return empty list
        val files = app.collectFiles(workingDir, listOf("some-file.kt"))

        files shouldBe emptyList()
      }

      it("should throw KtfmtCliException for directories outside working directory") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val outsideDir = fs.getPath("/outside")
        Files.createDirectories(workingDir)
        Files.createDirectories(outsideDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val exception = shouldThrow<KtfmtCliException> { app.collectFiles(workingDir, listOf("../outside")) }

        exception.message shouldBe "Directory '../outside' is outside the working directory"
      }
    }
  })
