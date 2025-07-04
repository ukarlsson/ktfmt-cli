package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class AppTest :
  DescribeSpec({
    describe("isIgnored function with jimfs") {
      it("should return false when ignore patterns is empty") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("test.kt")

        app.isIgnored(file, emptyList(), workingDir) shouldBe false
      }

      it("should ignore files matching exact pattern") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("build.kt")
        val patterns = listOf("build.kt")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should ignore files matching glob pattern") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val testDir = workingDir.resolve("src/test")
        Files.createDirectories(testDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = testDir.resolve("TestFile.kt")
        val patterns = listOf("src/test/**")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should ignore files matching wildcard pattern") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("generated.kt")
        val patterns = listOf("*.kt")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should not ignore files that don't match patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src/main")
        Files.createDirectories(srcDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = srcDir.resolve("App.kt")
        val patterns = listOf("build/**", "*.class")

        app.isIgnored(file, patterns, workingDir) shouldBe false
      }

      it("should handle multiple patterns correctly") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val buildDir = workingDir.resolve("build/classes")
        val targetDir = workingDir.resolve("target/generated")
        val srcDir = workingDir.resolve("src/main")
        Files.createDirectories(buildDir)
        Files.createDirectories(targetDir)
        Files.createDirectories(srcDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file1 = buildDir.resolve("Test.kt")
        val file2 = targetDir.resolve("Gen.kt")
        val file3 = srcDir.resolve("App.kt")
        val patterns = listOf("build/**", "target/**")

        app.isIgnored(file1, patterns, workingDir) shouldBe true
        app.isIgnored(file2, patterns, workingDir) shouldBe true
        app.isIgnored(file3, patterns, workingDir) shouldBe false
      }

      it("should handle nested directory patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val testDir = workingDir.resolve("src/test/kotlin/com/example")
        Files.createDirectories(testDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = testDir.resolve("Test.kt")
        val patterns = listOf("**/test/**")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should handle negation patterns to unignore files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val testDir = workingDir.resolve("src/test")
        val importantDir = workingDir.resolve("src/test/important")
        Files.createDirectories(testDir)
        Files.createDirectories(importantDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val ignoredFile = testDir.resolve("Test.kt")
        val unignoredFile = importantDir.resolve("ImportantTest.kt")
        val patterns = listOf("src/test/**", "!src/test/important/**")

        app.isIgnored(ignoredFile, patterns, workingDir) shouldBe true
        app.isIgnored(unignoredFile, patterns, workingDir) shouldBe false
      }

      it("should handle negation patterns for specific files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val buildDir = workingDir.resolve("build")
        Files.createDirectories(buildDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val ignoredFile = buildDir.resolve("generated.kt")
        val unignoredFile = buildDir.resolve("important.kt")
        val patterns = listOf("build/**", "!build/important.kt")

        app.isIgnored(ignoredFile, patterns, workingDir) shouldBe true
        app.isIgnored(unignoredFile, patterns, workingDir) shouldBe false
      }

      it("should handle multiple negation patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val testDir = workingDir.resolve("test")
        val srcDir = workingDir.resolve("src")
        Files.createDirectories(testDir)
        Files.createDirectories(srcDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val ignoredFile = testDir.resolve("Test.kt")
        val unignoredFile1 = testDir.resolve("Important.kt")
        val unignoredFile2 = srcDir.resolve("App.kt")
        val patterns = listOf("**/*.kt", "!test/Important.kt", "!src/App.kt")

        app.isIgnored(ignoredFile, patterns, workingDir) shouldBe true
        app.isIgnored(unignoredFile1, patterns, workingDir) shouldBe false
        app.isIgnored(unignoredFile2, patterns, workingDir) shouldBe false
      }

      it("should handle negation patterns that don't match anything") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val testDir = workingDir.resolve("test")
        Files.createDirectories(testDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = testDir.resolve("Test.kt")
        val patterns = listOf("test/**", "!nonexistent/**")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should process patterns sequentially - last match wins") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("important.kt")

        // Pattern order: ignore, unignore, ignore again
        val patterns = listOf("*.kt", "!important.kt", "*.kt")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should handle sequential patterns - unignore then ignore") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("test.kt")

        // Pattern order: ignore, unignore, ignore again
        val patterns = listOf("*.kt", "!test.kt", "test.kt")

        app.isIgnored(file, patterns, workingDir) shouldBe true
      }

      it("should handle sequential patterns - ignore then unignore") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val file = workingDir.resolve("test.kt")

        // Pattern order: ignore, unignore (final state: unignored)
        val patterns = listOf("*.kt", "!test.kt")

        app.isIgnored(file, patterns, workingDir) shouldBe false
      }
    }

    describe("command line validation with jimfs") {
      it("should require exactly one mode flag") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test no flags - should fail
        app.run(arrayOf()) shouldBe 1

        // Test multiple flags - should fail
        app.run(arrayOf("--write", "--check")) shouldBe 1
      }

      it("should accept single mode flag") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test single flag - should succeed
        app.run(arrayOf("--write")) shouldBe 0
      }
    }
  })
