package io.github.ukarlsson.ktfmtcli

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path

class CacheManagerTest :
  StringSpec({
    fun createTestFileSystem() = Jimfs.newFileSystem(Configuration.unix())

    "should return empty cache when cache file doesn't exist" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val cacheManager = CacheManager(cacheFile)

      val cache = cacheManager.loadCache()
      cache.shouldBeEmpty()
    }

    "should load cache from existing file" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())
      Files.write(cacheFile, listOf("123456,${testFile.toAbsolutePath()}"))

      val cacheManager = CacheManager(cacheFile)
      val cache = cacheManager.loadCache()

      cache.shouldContainKey(testFile.toAbsolutePath().normalize())
      cache[testFile.toAbsolutePath().normalize()] shouldBe 123456
    }

    "should ignore malformed cache lines" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())
      Files.write(
        cacheFile,
        listOf(
          "123456,${testFile.toAbsolutePath()}",
          "invalid-line",
          "not-a-number,/some/path",
          "789,${testFile.toAbsolutePath()}_other",
        ),
      )

      val cacheManager = CacheManager(cacheFile)
      val cache = cacheManager.loadCache()

      cache.size shouldBe 2
      cache.shouldContainKey(testFile.toAbsolutePath().normalize())
      cache[testFile.toAbsolutePath().normalize()] shouldBe 123456
    }

    "should return empty cache when file is corrupted" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")

      // Create a directory with the cache file name to simulate corruption
      Files.createDirectory(cacheFile)

      val cacheManager = CacheManager(cacheFile)
      val cache = cacheManager.loadCache()

      cache.shouldBeEmpty()
    }

    "should save cache to file" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile1 = fs.getPath("/test1.kt")
      val testFile2 = fs.getPath("/test2.kt")

      Files.write(testFile1, "fun main() {}\n".toByteArray())
      Files.write(testFile2, "fun test() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val cache =
        mapOf(testFile1.toAbsolutePath().normalize() to 123456, testFile2.toAbsolutePath().normalize() to 789012)

      cacheManager.saveCache(cache)

      val lines = Files.readAllLines(cacheFile)
      lines.size shouldBe 2
      lines.any { it.startsWith("123456,") && it.endsWith(testFile1.toAbsolutePath().normalize().toString()) } shouldBe
        true
      lines.any { it.startsWith("789012,") && it.endsWith(testFile2.toAbsolutePath().normalize().toString()) } shouldBe
        true
    }

    "should create parent directories when saving cache" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/parent/nested/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val cache = mapOf(testFile.toAbsolutePath().normalize() to 123456)

      cacheManager.saveCache(cache)

      Files.exists(cacheFile) shouldBe true
      val lines = Files.readAllLines(cacheFile)
      lines.size shouldBe 1
      lines[0] shouldBe "123456,${testFile.toAbsolutePath().normalize()}"
    }

    "should handle save errors gracefully" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/readonly/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      // Create readonly parent directory
      Files.createDirectory(fs.getPath("/readonly"))
      // Jimfs doesn't support toFile(), so we'll create a different error scenario
      // Try to save to a path that is a directory instead of a file
      Files.createDirectory(cacheFile)

      val cacheManager = CacheManager(cacheFile)
      val cache = mapOf(testFile.toAbsolutePath().normalize() to 123456)

      // Should not throw exception
      cacheManager.saveCache(cache)
    }

    "should calculate hash for file content" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val hash1 = cacheManager.calculateHash(testFile)
      val hash2 = cacheManager.calculateHash(testFile)

      hash1 shouldBe hash2
      hash1 shouldNotBe 0
    }

    "should detect file changes with different hashes" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val hash1 = cacheManager.calculateHash(testFile)

      // Modify file
      Files.write(testFile, "fun main() { println(\"Hello\") }\n".toByteArray())
      val hash2 = cacheManager.calculateHash(testFile)

      hash1 shouldNotBe hash2
    }

    "should skip file when hash matches cache" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val currentHash = cacheManager.calculateHash(testFile)
      val cache = mapOf(testFile.toAbsolutePath().normalize() to currentHash)

      cacheManager.shouldSkipFile(testFile, cache) shouldBe true
    }

    "should not skip file when hash doesn't match cache" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val cache = mapOf(testFile.toAbsolutePath().normalize() to 999999) // Wrong hash

      cacheManager.shouldSkipFile(testFile, cache) shouldBe false
    }

    "should not skip file when not in cache" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/test.kt")

      Files.write(testFile, "fun main() {}\n".toByteArray())

      val cacheManager = CacheManager(cacheFile)
      val cache = emptyMap<Path, Int>()

      cacheManager.shouldSkipFile(testFile, cache) shouldBe false
    }

    "should not skip file when file cannot be read" {
      val fs = createTestFileSystem()
      val cacheFile = fs.getPath("/cache.txt")
      val testFile = fs.getPath("/nonexistent.kt")

      val cacheManager = CacheManager(cacheFile)
      val cache = mapOf(testFile.toAbsolutePath().normalize() to 123456)

      cacheManager.shouldSkipFile(testFile, cache) shouldBe false
    }
  })
