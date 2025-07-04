package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class FileCollectionTest :
  DescribeSpec({
    describe("file collection with jimfs") {
      it("should collect Kotlin files matching glob patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src/main/kotlin")
        val buildDir = workingDir.resolve("build")
        Files.createDirectories(srcDir)
        Files.createDirectories(buildDir)

        // Create test files
        val file1 = srcDir.resolve("App.kt")
        val file2 = srcDir.resolve("Test.kt")
        val file3 = buildDir.resolve("Generated.kt")

        Files.write(file1, "class App".toByteArray())
        Files.write(file2, "class Test".toByteArray())
        Files.write(file3, "class Generated".toByteArray())

        // Create ignore file
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "build/**".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*.kt"))

        // Should include source files
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Test.kt"

        // Should exclude ignored files
        files.map { it.fileName.toString() } shouldNotContain "Generated.kt"
      }

      it("should handle empty glob patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, emptyList())
        files shouldBe emptyList()
      }

      it("should handle non-existent directories gracefully") {
        val fs = Jimfs.newFileSystem()
        val nonExistentDir = fs.getPath("/non/existent/directory")

        val app = App(fileSystem = fs, workingDirectory = nonExistentDir)

        // This should not throw an exception
        val files = app.collectFiles(nonExistentDir, listOf("**/*.kt"))
        files shouldBe emptyList()
      }

      it("should collect files from multiple glob patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        val testDir = workingDir.resolve("test")
        Files.createDirectories(srcDir)
        Files.createDirectories(testDir)

        // Create test files
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(testDir.resolve("Test.kt"), "class Test".toByteArray())
        Files.write(workingDir.resolve("Build.kt"), "class Build".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("src/*.kt", "test/*.kt"))

        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Test.kt"
        files.map { it.fileName.toString() } shouldNotContain "Build.kt" // Not matched by patterns
      }

      it("should handle deeply nested directory structures") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val deepDir = workingDir.resolve("src/main/kotlin/com/example/app")
        Files.createDirectories(deepDir)

        // Create test files at different levels
        Files.write(workingDir.resolve("src/App.kt"), "class App".toByteArray())
        Files.write(workingDir.resolve("src/main/Main.kt"), "class Main".toByteArray())
        Files.write(deepDir.resolve("Service.kt"), "class Service".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*.kt"))

        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Main.kt"
        files.map { it.fileName.toString() } shouldContain "Service.kt"
      }
    }

    describe("glob pattern matching with jimfs") {
      it("should correctly match various patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        val testDir = workingDir.resolve("test")
        Files.createDirectories(srcDir)
        Files.createDirectories(testDir)

        // Create various test files
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(testDir.resolve("Test.kt"), "class Test".toByteArray())
        Files.write(workingDir.resolve("Root.kt"), "class Root".toByteArray())
        Files.write(workingDir.resolve("README.md"), "# README".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test specific directory pattern
        var files = app.collectFiles(workingDir, listOf("src/*.kt"))
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldNotContain "Test.kt"
        files.map { it.fileName.toString() } shouldNotContain "Root.kt"

        // Test recursive pattern - include both root level and nested files
        files = app.collectFiles(workingDir, listOf("*.kt", "**/*.kt"))
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Test.kt"
        files.map { it.fileName.toString() } shouldContain "Root.kt"
        files.map { it.fileName.toString() } shouldNotContain "README.md"
      }
    }

    describe("Kotlin file filtering with jimfs") {
      it("should only include .kt and .kts files from any glob pattern") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        Files.createDirectories(srcDir)

        // Create various file types
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Script.kts"), "println(\"hello\")".toByteArray())
        Files.write(srcDir.resolve("Main.java"), "public class Main {}".toByteArray())
        Files.write(srcDir.resolve("config.xml"), "<config></config>".toByteArray())
        Files.write(srcDir.resolve("README.md"), "# README".toByteArray())
        Files.write(srcDir.resolve("build.gradle"), "plugins {}".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("src/*"))

        // Should only include .kt and .kts files
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Script.kts"

        // Should exclude all other file types
        files.map { it.fileName.toString() } shouldNotContain "Main.java"
        files.map { it.fileName.toString() } shouldNotContain "config.xml"
        files.map { it.fileName.toString() } shouldNotContain "README.md"
        files.map { it.fileName.toString() } shouldNotContain "build.gradle"
      }

      it("should filter .kt and .kts files from wildcard patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        Files.createDirectories(workingDir)

        // Create mixed file types in root
        Files.write(workingDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(workingDir.resolve("Script.kts"), "println(\"hello\")".toByteArray())
        Files.write(workingDir.resolve("Main.java"), "public class Main {}".toByteArray())
        Files.write(workingDir.resolve("README.md"), "# README".toByteArray())
        Files.write(workingDir.resolve("build.gradle.kts"), "plugins {}".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("*"))

        // Should only include .kt and .kts files (including .gradle.kts)
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Script.kts"
        files.map { it.fileName.toString() } shouldContain "build.gradle.kts"

        // Should exclude non-Kotlin files
        files.map { it.fileName.toString() } shouldNotContain "Main.java"
        files.map { it.fileName.toString() } shouldNotContain "README.md"
      }

      it("should filter .kt and .kts files from recursive patterns") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src/main/kotlin")
        val testDir = workingDir.resolve("src/test/kotlin")
        val resourcesDir = workingDir.resolve("src/main/resources")
        Files.createDirectories(srcDir)
        Files.createDirectories(testDir)
        Files.createDirectories(resourcesDir)

        // Create mixed file types in nested directories
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Helper.java"), "public class Helper {}".toByteArray())
        Files.write(testDir.resolve("Test.kt"), "class Test".toByteArray())
        Files.write(testDir.resolve("Script.kts"), "println(\"test\")".toByteArray())
        Files.write(resourcesDir.resolve("config.properties"), "key=value".toByteArray())
        Files.write(workingDir.resolve("build.gradle"), "plugins {}".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*"))

        // Should only include .kt and .kts files from all directories
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Test.kt"
        files.map { it.fileName.toString() } shouldContain "Script.kts"

        // Should exclude all other file types
        files.map { it.fileName.toString() } shouldNotContain "Helper.java"
        files.map { it.fileName.toString() } shouldNotContain "config.properties"
        files.map { it.fileName.toString() } shouldNotContain "build.gradle"
      }

      it("should filter direct file paths to only .kt and .kts files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        Files.createDirectories(srcDir)

        // Create various file types
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Script.kts"), "println(\"hello\")".toByteArray())
        Files.write(srcDir.resolve("Main.java"), "public class Main {}".toByteArray())
        Files.write(srcDir.resolve("README.md"), "# README".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test direct .kt file path
        var files = app.collectFiles(workingDir, listOf("src/App.kt"))
        files.map { it.fileName.toString() } shouldContain "App.kt"

        // Test direct .kts file path
        files = app.collectFiles(workingDir, listOf("src/Script.kts"))
        files.map { it.fileName.toString() } shouldContain "Script.kts"

        // Test direct non-Kotlin file path - should be filtered out
        files = app.collectFiles(workingDir, listOf("src/Main.java"))
        files.map { it.fileName.toString() } shouldNotContain "Main.java"
        files shouldBe emptyList()

        // Test direct non-Kotlin file path - should be filtered out
        files = app.collectFiles(workingDir, listOf("src/README.md"))
        files.map { it.fileName.toString() } shouldNotContain "README.md"
        files shouldBe emptyList()
      }

      it("should handle directory arguments and filter for .kt and .kts files") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        val subDir = srcDir.resolve("subpackage")
        Files.createDirectories(subDir)

        // Create mixed file types in directories
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Script.kts"), "println(\"hello\")".toByteArray())
        Files.write(srcDir.resolve("Main.java"), "public class Main {}".toByteArray())
        Files.write(subDir.resolve("Service.kt"), "class Service".toByteArray())
        Files.write(subDir.resolve("config.properties"), "key=value".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("src"))

        // Should include all .kt and .kts files in the directory tree
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Script.kts"
        files.map { it.fileName.toString() } shouldContain "Service.kt"

        // Should exclude all other file types
        files.map { it.fileName.toString() } shouldNotContain "Main.java"
        files.map { it.fileName.toString() } shouldNotContain "config.properties"
      }

      it("should handle mixed arguments with filtering") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        val testDir = workingDir.resolve("test")
        Files.createDirectories(srcDir)
        Files.createDirectories(testDir)

        // Create mixed file types
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Helper.java"), "public class Helper {}".toByteArray())
        Files.write(testDir.resolve("Test.kt"), "class Test".toByteArray())
        Files.write(testDir.resolve("Script.kts"), "println(\"test\")".toByteArray())
        Files.write(workingDir.resolve("build.gradle"), "plugins {}".toByteArray())
        Files.write(workingDir.resolve("Main.kt"), "class Main".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Mix of direct file, directory, and glob pattern
        val files = app.collectFiles(workingDir, listOf("Main.kt", "src", "test/*.kts"))

        // Should include .kt and .kts files that match the criteria
        files.map { it.fileName.toString() } shouldContain "Main.kt" // Direct file
        files.map { it.fileName.toString() } shouldContain "App.kt" // From directory
        files.map { it.fileName.toString() } shouldContain "Script.kts" // From glob pattern

        // Should exclude non-Kotlin files and non-matching patterns
        files.map { it.fileName.toString() } shouldNotContain "Helper.java" // Non-Kotlin from directory
        files.map { it.fileName.toString() } shouldNotContain "Test.kt" // .kt file not matching glob pattern
        files.map { it.fileName.toString() } shouldNotContain "build.gradle" // Non-Kotlin file
      }

      it("should handle empty results when no .kt or .kts files match") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val srcDir = workingDir.resolve("src")
        Files.createDirectories(srcDir)

        // Create only non-Kotlin files
        Files.write(srcDir.resolve("Main.java"), "public class Main {}".toByteArray())
        Files.write(srcDir.resolve("config.xml"), "<config></config>".toByteArray())
        Files.write(srcDir.resolve("README.md"), "# README".toByteArray())
        Files.write(workingDir.resolve("build.gradle"), "plugins {}".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*"))

        // Should return empty list since no .kt or .kts files exist
        files shouldBe emptyList()
      }
    }

    describe("Performance optimizations with jimfs") {
      it("should skip traversing ignored directories for performance") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")
        val nodeModulesDir = workingDir.resolve("node_modules/package/deeply/nested")
        val buildDir = workingDir.resolve("build/classes/kotlin")
        val srcDir = workingDir.resolve("src/main/kotlin")
        Files.createDirectories(nodeModulesDir)
        Files.createDirectories(buildDir)
        Files.createDirectories(srcDir)

        // Create .ktfmtignore with performance-critical patterns
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "node_modules/**\nbuild/**".toByteArray())

        // Create many files in ignored directories (simulating performance issues)
        Files.write(nodeModulesDir.resolve("index.kt"), "// Should be ignored".toByteArray())
        Files.write(nodeModulesDir.resolve("util.kts"), "// Should be ignored".toByteArray())
        Files.write(buildDir.resolve("Generated.kt"), "// Should be ignored".toByteArray())

        // Create files in non-ignored directories
        Files.write(srcDir.resolve("App.kt"), "class App".toByteArray())
        Files.write(srcDir.resolve("Service.kt"), "class Service".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*"))

        // Should only include files from non-ignored directories
        files.map { it.fileName.toString() } shouldContain "App.kt"
        files.map { it.fileName.toString() } shouldContain "Service.kt"

        // Should exclude all files from ignored directories
        files.map { it.fileName.toString() } shouldNotContain "index.kt"
        files.map { it.fileName.toString() } shouldNotContain "util.kts"
        files.map { it.fileName.toString() } shouldNotContain "Generated.kt"

        // Verify we only got the expected files
        files.size shouldBe 2
      }

      it("should handle deep directory structures efficiently") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")

        // Create deep nested structure with ignored directory
        val deepIgnoredDir = workingDir.resolve("node_modules/package/lib/deep/nested/very/deep")
        val deepSrcDir = workingDir.resolve("src/main/kotlin/com/example/app/service/impl")
        Files.createDirectories(deepIgnoredDir)
        Files.createDirectories(deepSrcDir)

        // Create .ktfmtignore
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "node_modules/**".toByteArray())

        // Create files in both deep directories
        Files.write(deepIgnoredDir.resolve("module.kt"), "// Ignored".toByteArray())
        Files.write(deepSrcDir.resolve("ServiceImpl.kt"), "class ServiceImpl".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)
        val files = app.collectFiles(workingDir, listOf("**/*"))

        // Should only find the non-ignored file
        files.map { it.fileName.toString() } shouldContain "ServiceImpl.kt"
        files.map { it.fileName.toString() } shouldNotContain "module.kt"
        files.size shouldBe 1
      }

      it("should skip top-level glob patterns that match ktfmtignore") {
        val fs = Jimfs.newFileSystem()
        val workingDir = fs.getPath("/project")

        // Create directories with Kotlin files
        val nodeModulesDir = workingDir.resolve("node_modules/some-lib")
        val srcDir = workingDir.resolve("src")
        Files.createDirectories(nodeModulesDir)
        Files.createDirectories(srcDir)

        Files.write(nodeModulesDir.resolve("index.kt"), "fun lib() {}".toByteArray())
        Files.write(srcDir.resolve("App.kt"), "fun main() {}".toByteArray())

        // Add ignore pattern for node_modules
        val ignoreFile = workingDir.resolve(".ktfmtignore")
        Files.write(ignoreFile, "node_modules".toByteArray())

        val app = App(fileSystem = fs, workingDirectory = workingDir)

        // Test that direct glob for node_modules returns empty (should be skipped)
        val nodeModulesFiles = app.collectFiles(workingDir, listOf("node_modules"))
        nodeModulesFiles.size shouldBe 0

        // Test that wildcard still works for non-ignored directories
        val allFiles = app.collectFiles(workingDir, listOf("**/*.kt"))
        allFiles.size shouldBe 1
        allFiles[0].fileName.toString() shouldBe "App.kt"
      }
    }
  })
