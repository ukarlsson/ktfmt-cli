package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class KtfmtIgnoreTest :
  DescribeSpec({
    describe(".ktfmtignore file parsing with jimfs") {
      it("should return empty list when .ktfmtignore doesn't exist") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)

        result.patterns.shouldBeEmpty()
      }

      it("should parse patterns from .ktfmtignore file") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create .ktfmtignore file with test patterns
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        val ignoreContent =
          """
                # This is a comment
                build/**
                target/**
                *.class
                
                # Another comment
                **/generated/**
            """
            .trimIndent()

        Files.write(ignoreFile, ignoreContent.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)
        val patterns = result.patterns

        patterns shouldHaveSize 4
        patterns shouldContain "build/**"
        patterns shouldContain "target/**"
        patterns shouldContain "*.class"
        patterns shouldContain "**/generated/**"

        // Should not contain comments or empty lines
        patterns shouldNotContain "# This is a comment"
        patterns shouldNotContain "# Another comment"
        patterns shouldNotContain ""
      }

      it("should handle empty .ktfmtignore file") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create empty .ktfmtignore file
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)

        result.patterns.shouldBeEmpty()
      }

      it("should handle .ktfmtignore with only comments and empty lines") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create .ktfmtignore file with only comments and empty lines
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        val ignoreContent =
          """
                # Comment 1
                
                # Comment 2
                
                
                # Comment 3
            """
            .trimIndent()

        Files.write(ignoreFile, ignoreContent.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)

        result.patterns.shouldBeEmpty()
      }

      it("should trim whitespace from patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create .ktfmtignore file with whitespace around patterns
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        val ignoreContent =
          """
                |  build/**  
                |	target/**
                | *.class 
            """
            .trimMargin()

        Files.write(ignoreFile, ignoreContent.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)
        val patterns = result.patterns

        patterns shouldHaveSize 3
        patterns shouldContain "build/**"
        patterns shouldContain "target/**"
        patterns shouldContain "*.class"

        // Should not contain patterns with whitespace
        patterns shouldNotContain "  build/**  "
        patterns shouldNotContain "	target/**	"
        patterns shouldNotContain " *.class "
      }

      it("should handle complex ignore patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val ignoreFile = workingDir.resolve(".ktfmtignore")
        val ignoreContent =
          """
                # Build directories
                build/**
                target/**
                out/**
                
                # Generated files
                **/generated/**
                **/*Generated.kt
                
                # Temporary files
                *.tmp
                *.temp
                .DS_Store
                
                # IDE files
                .idea/**
                *.iml
                .vscode/**
            """
            .trimIndent()

        Files.write(ignoreFile, ignoreContent.toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val result = app.loadKtfmtIgnore(workingDir)
        val patterns = result.patterns

        patterns shouldHaveSize 11
        patterns shouldContain "build/**"
        patterns shouldContain "**/generated/**"
        patterns shouldContain "**/*Generated.kt"
        patterns shouldContain "*.tmp"
        patterns shouldContain ".idea/**"
      }
    }

    describe("ignore pattern application with jimfs") {
      it("should correctly apply common ignore patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")

        // Create directory structure
        val buildDir = workingDir.resolve("build/classes")
        val targetDir = workingDir.resolve("target/kotlin-classes")
        val srcDir = workingDir.resolve("src/main/kotlin")
        val generatedDir = workingDir.resolve("src/main/generated")
        val nodeModulesDir = workingDir.resolve("frontend/node_modules/package")

        Files.createDirectories(buildDir)
        Files.createDirectories(targetDir)
        Files.createDirectories(srcDir)
        Files.createDirectories(generatedDir)
        Files.createDirectories(nodeModulesDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val patterns = listOf("build/**", "target/**", "*.class", "**/generated/**", "**/node_modules/**")

        // Files that should be ignored
        val ignoredFiles =
          listOf(
            buildDir.resolve("Main.kt"),
            targetDir.resolve("Test.kt"),
            workingDir.resolve("App.class"),
            generatedDir.resolve("Proto.kt"),
            nodeModulesDir.resolve("index.kt"),
          )

        ignoredFiles.forEach { file -> app.isIgnored(file, patterns, workingDir) shouldBe true }

        // Files that should not be ignored
        val allowedFiles =
          listOf(
            srcDir.resolve("App.kt"),
            workingDir.resolve("src/test/kotlin/Test.kt"),
            workingDir.resolve("README.kt"), // Unlikely but possible
            workingDir.resolve("docs/examples/Example.kt"),
          )

        allowedFiles.forEach { file -> app.isIgnored(file, patterns, workingDir) shouldBe false }
      }

      it("should handle edge cases in pattern matching") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test exact file name matching
        val patterns = listOf("build.gradle.kts", "settings.gradle.kts")
        app.isIgnored(workingDir.resolve("build.gradle.kts"), patterns, workingDir) shouldBe true
        app.isIgnored(workingDir.resolve("app/build.gradle.kts"), patterns, workingDir) shouldBe false

        // Test extension matching
        val extPatterns = listOf("*.jar", "*.war")
        app.isIgnored(workingDir.resolve("app.jar"), extPatterns, workingDir) shouldBe true
        app.isIgnored(workingDir.resolve("src/app.jar"), extPatterns, workingDir) shouldBe false // Only matches in root

        // Test double star patterns - using build/** since it's at the root
        val doubleStarPatterns = listOf("build/**")
        app.isIgnored(workingDir.resolve("build/classes/App.kt"), doubleStarPatterns, workingDir) shouldBe true

        // Test actual **/build/** pattern with nested structure
        val nestedDoubleStarPatterns = listOf("**/build/**")
        app.isIgnored(workingDir.resolve("module1/build/classes/App.kt"), nestedDoubleStarPatterns, workingDir) shouldBe
          true
      }
    }

    describe(".ktfmtignore upward search with jimfs") {
      it("should find .ktfmtignore in parent directories") {
        val fs = Jimfs.newFileSystem()
        val rootDir = fs.getPath("/project")
        val subDir = rootDir.resolve("submodule/src/main/kotlin")
        Files.createDirectories(subDir)

        // Create .ktfmtignore in root directory
        val ignoreFile = rootDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "build/**\ngenerated/**".toByteArray())

        // Create app with working directory in subdirectory
        val app = App(fileSystem = fs, workingDirectory = subDir)
        val result = app.loadKtfmtIgnore(subDir)
        val patterns = result.patterns

        patterns shouldHaveSize 2
        patterns shouldContain "build/**"
        patterns shouldContain "generated/**"
      }

      it("should use closest .ktfmtignore when multiple exist") {
        val fs = Jimfs.newFileSystem()
        val rootDir = fs.getPath("/project")
        val subDir = rootDir.resolve("submodule")
        val deepDir = subDir.resolve("src/main/kotlin")
        Files.createDirectories(deepDir)

        // Create .ktfmtignore in root with general patterns
        val rootIgnoreFile = rootDir.resolve(".ktfmtignore")
        Files.write(rootIgnoreFile, "build/**\ntarget/**".toByteArray())

        // Create more specific .ktfmtignore in subdirectory
        val subIgnoreFile = subDir.resolve(".ktfmtignore")
        Files.write(subIgnoreFile, "generated/**\n*.tmp".toByteArray())

        // Create app with working directory in deep directory
        val app = App(fileSystem = fs, workingDirectory = deepDir)
        val result = app.loadKtfmtIgnore(deepDir)
        val patterns = result.patterns

        // Should find the closest .ktfmtignore (in submodule/)
        patterns shouldHaveSize 2
        patterns shouldContain "generated/**"
        patterns shouldContain "*.tmp"

        // Should not contain root-level patterns
        patterns shouldNotContain "build/**"
        patterns shouldNotContain "target/**"
      }

      it("should return empty list when no .ktfmtignore found in hierarchy") {
        val fs = Jimfs.newFileSystem()
        val deepDir = fs.getPath("/project/submodule/src/main/kotlin")
        Files.createDirectories(deepDir)

        // No .ktfmtignore files anywhere
        val app = App(fileSystem = fs, workingDirectory = deepDir)
        val result = app.loadKtfmtIgnore(deepDir)
        val patterns = result.patterns

        patterns.shouldBeEmpty()
      }

      it("should search .ktfmtignore relative to App's working directory, not file locations") {
        val fs = Jimfs.newFileSystem()
        val cwd = fs.getPath("/project") // Current working directory
        val otherDir = fs.getPath("/other-project") // Different project
        Files.createDirectories(cwd)
        Files.createDirectories(otherDir.resolve("src"))

        // Create .ktfmtignore in CWD
        val cwdIgnoreFile = cwd.resolve(".ktfmtignore")
        Files.write(cwdIgnoreFile, "build/**\ngenerated/**".toByteArray())

        // Create different .ktfmtignore in other directory
        val otherIgnoreFile = otherDir.resolve(".ktfmtignore")
        Files.write(otherIgnoreFile, "target/**\n*.class".toByteArray())

        // App working directory is CWD, but we're collecting files from other directory
        val app = App(fileSystem = fs, workingDirectory = cwd)
        val result = app.loadKtfmtIgnore() // Uses App's workingDirectory, not file locations
        val patterns = result.patterns

        // Should find patterns from CWD, not from other-project
        patterns shouldHaveSize 2
        patterns shouldContain "build/**"
        patterns shouldContain "generated/**"

        // Should NOT contain patterns from other-project
        patterns shouldNotContain "target/**"
        patterns shouldNotContain "*.class"
      }

      it("REGRESSION: should resolve ignore patterns relative to .ktfmtignore file directory, not CWD") {
        val fs = Jimfs.newFileSystem()
        val projectRoot = fs.getPath("/project")
        val subDir = projectRoot.resolve("submodule")
        val buildDir = projectRoot.resolve("build")
        Files.createDirectories(subDir)
        Files.createDirectories(buildDir)

        // Create .ktfmtignore in project root with patterns relative to project root
        val ignoreFile = projectRoot.resolve(".ktfmtignore")
        Files.write(ignoreFile, "build/**\ngenerated/**".toByteArray())

        // Create files that should be ignored
        val buildFile = buildDir.resolve("Main.kt")
        Files.createFile(buildFile)
        val generatedFile = projectRoot.resolve("generated/Proto.kt")
        Files.createDirectories(generatedFile.parent)
        Files.createFile(generatedFile)

        // Create app with working directory in subdirectory (simulating running from submodule)
        val app = App(fileSystem = fs, workingDirectory = subDir)
        val result = app.loadKtfmtIgnore(subDir) // Searches upward from subDir, finds .ktfmtignore in projectRoot
        val patterns = result.patterns
        val ignoreBaseDir = result.baseDirectory

        // FIXED: Now patterns are resolved relative to ignoreBaseDir (projectRoot), not subDir
        // The build file should be ignored because:
        // - .ktfmtignore is found in /project/
        // - Pattern "build/**" is resolved relative to /project/ (ignoreBaseDir)
        // - File /project/build/Main.kt matches /project/build/** pattern

        // This should now be true after the fix
        app.isIgnored(buildFile, patterns, ignoreBaseDir) shouldBe true

        // This demonstrates the fix: ignoreBaseDir should be projectRoot
        ignoreBaseDir shouldBe projectRoot
      }
    }
  })
