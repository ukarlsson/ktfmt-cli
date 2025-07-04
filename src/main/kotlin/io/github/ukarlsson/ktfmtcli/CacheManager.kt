package io.github.ukarlsson.ktfmtcli

import com.facebook.ktfmt.format.FormattingOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.apache.commons.codec.digest.MurmurHash3

class CacheManager(private val cacheFile: Path) {

  fun loadCache(): Map<Path, Int> {
    if (!Files.exists(cacheFile)) {
      return emptyMap()
    }

    return try {
      Files.readAllLines(cacheFile)
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val parts = line.split(',', limit = 2)
          if (parts.size == 2) {
            val hash = parts[0].toIntOrNull()
            val path = cacheFile.fileSystem.getPath(parts[1])
            if (hash != null) {
              path to hash
            } else null
          } else null
        }
        .toMap()
    } catch (e: java.io.IOException) {
      // If cache file is corrupted or unreadable, return empty cache
      emptyMap()
    } catch (e: java.lang.NumberFormatException) {
      // If hash parsing fails, return empty cache
      emptyMap()
    }
  }

  fun saveCache(cache: Map<Path, Int>) {
    try {
      val lines = cache.map { (path, hash) -> "$hash,${path.toAbsolutePath().normalize()}" }

      cacheFile.parent?.let { parent -> Files.createDirectories(parent) }

      Files.write(cacheFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    } catch (e: java.io.IOException) {
      // If we can't write cache, continue without caching
      // This is not a fatal error
    }
  }

  fun calculateHash(file: Path, options: FormattingOptions): Int =
    MurmurHash3.hash32x86(options.toString().toByteArray() + Files.readAllBytes(file))

  fun shouldSkipFile(file: Path, cache: Map<Path, Int>, options: FormattingOptions): Boolean {
    val cachedHash = cache[file.toAbsolutePath().normalize()] ?: return false
    return try {
      val currentHash = calculateHash(file, options)
      currentHash == cachedHash
    } catch (e: java.io.IOException) {
      // If we can't read the file, don't skip it
      false
    }
  }
}
