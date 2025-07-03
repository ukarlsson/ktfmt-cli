package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ExtractBaseDirectoryTest :
  DescribeSpec({
    describe("extractBaseDirectory") {
      it("should extract base directory from simple glob pattern") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("src/main/kotlin/**.kt", workingDir)

        baseDir shouldBe workingDir.resolve("src/main/kotlin")
        remainingPattern shouldBe "**.kt"
      }

      it("should extract base directory from nested path with glob") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("gradle-project-root/core/**.kt", workingDir)

        baseDir shouldBe workingDir.resolve("gradle-project-root/core")
        remainingPattern shouldBe "**.kt"
      }

      it("should handle glob in first component") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("**/App.kt", workingDir)

        baseDir shouldBe workingDir
        remainingPattern shouldBe "**/App.kt"
      }

      it("should handle pattern with no glob characters") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("src/main/App.kt", workingDir)

        baseDir shouldBe workingDir
        remainingPattern shouldBe "src/main/App.kt"
      }

      it("should handle glob with question mark") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("src/main/App?.kt", workingDir)

        baseDir shouldBe workingDir.resolve("src/main")
        remainingPattern shouldBe "App?.kt"
      }

      it("should handle glob with brackets") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("src/main/App[Test].kt", workingDir)

        baseDir shouldBe workingDir.resolve("src/main")
        remainingPattern shouldBe "App[Test].kt"
      }

      it("should handle glob with braces") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("src/main/App{Test,Impl}.kt", workingDir)

        baseDir shouldBe workingDir.resolve("src/main")
        remainingPattern shouldBe "App{Test,Impl}.kt"
      }

      it("should handle complex multi-level glob") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("a/b/c/**/d/e/*.kt", workingDir)

        baseDir shouldBe workingDir.resolve("a/b/c")
        remainingPattern shouldBe "**/d/e/*.kt"
      }

      it("should handle single component with glob") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        val (baseDir, remainingPattern) = app.extractBaseDirectory("*.kt", workingDir)

        baseDir shouldBe workingDir
        remainingPattern shouldBe "*.kt"
      }
    }
  })
