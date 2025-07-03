# ktfmt-cli

A command-line interface for [ktfmt](https://github.com/facebook/ktfmt), the Kotlin code formatter.

## Features

- Format Kotlin files using ktfmt's formatting engine
- Support for multiple formatting styles (Meta, Google, KotlinLang)
- Support for glob patterns, direct file paths, and directories
- Automatically filters for .kt and .kts files only
- All ktfmt formatting options supported
- Check mode for CI/CD integration
- `.ktfmtignore` file support
- Single executable JAR with all dependencies included

## Installation

### Download from Releases

Download the latest JAR from the [Releases page](https://github.com/ukarlsson/ktfmt-cli/releases):

```bash
# Download the latest release
wget https://github.com/ukarlsson/ktfmt-cli/releases/latest/download/ktfmt-cli-1.0.0-ktfmt0.47.jar

# Make it executable (optional)
chmod +x ktfmt-cli-1.0.0-ktfmt0.47.jar
```

### Build from Source

```bash
git clone https://github.com/ukarlsson/ktfmt-cli.git
cd ktfmt-cli
./gradlew shadowJar
```

## Usage

### Basic Usage

**Format files** (writes changes to disk):
```bash
java -jar ktfmt-cli.jar --write
java -jar ktfmt-cli.jar --write src/**/*.kt
java -jar ktfmt-cli.jar --write src/main/kotlin/App.kt
```

**Check files** (for CI/CD, exits 1 if formatting needed):
```bash
java -jar ktfmt-cli.jar --check
java -jar ktfmt-cli.jar --check src/**/*.kt
```

### Command-Line Options

```
Usage: ktfmt-cli [options] [globs...]

Arguments:
    globs                           Files, directories, or glob patterns to format (default: **/*)

Mode Options (required):
    --write                         Write formatting changes to files
    --check                         Check if files need formatting, exit 1 if so

Formatting Options:
    --style [meta]                  Formatting style: meta (default), google, or kotlinlang
    --max-width, -w [100]          Maximum line width
    --block-indent=<int>            Block indent size in spaces (overrides style default)
    --continuation-indent=<int>     Continuation indent size in spaces (overrides style default)
    --remove-unused-imports [true]  Remove unused imports
    --manage-trailing-commas        Automatically add/remove trailing commas (defaults by style)

Other Options:
    --version, -V                   Print version information
    --help, -h                      Show help message
```

### Examples

**Format all Kotlin files (.kt and .kts):**
```bash
java -jar ktfmt-cli.jar --write
```

**Check formatting (CI/CD):**
```bash
java -jar ktfmt-cli.jar --check
```

**Format specific files:**
```bash
java -jar ktfmt-cli.jar --write src/main/kotlin/App.kt src/test/kotlin/AppTest.kt
```

**Format a directory:**
```bash
java -jar ktfmt-cli.jar --write src/main/kotlin/
```

**Format with Google style:**
```bash
java -jar ktfmt-cli.jar --write --style=google
```

**Format this project:**
```bash
./ktfmt-cli --write --style=google --max-width=120
```

**Custom formatting options:**
```bash
java -jar ktfmt-cli.jar --write --max-width=120 --block-indent=4 --continuation-indent=8
```

**Format with trailing comma management:**
```bash
# Override style default (e.g., enable for meta style)
java -jar ktfmt-cli.jar --write --style=meta --manage-trailing-commas=true

# Google and kotlinlang styles enable trailing commas by default
java -jar ktfmt-cli.jar --write --style=google
```

**Check files outside working directory:**
```bash
java -jar ktfmt-cli.jar --check ../other-project/src/**/*.kt
```

### Formatting Styles

| Style | Block Indent | Continuation Indent | Trailing Commas | Description |
|-------|--------------|-------------------|-----------------|-------------|
| `meta` (default) | 2 | 4 | false | Meta's Kotlin style |
| `google` | 2 | 2 | true | Google's Kotlin style |
| `kotlinlang` | 4 | 4 | true | kotlin.org style |

You can override style defaults with `--block-indent` and `--continuation-indent`.

### .ktfmtignore File

Create a `.ktfmtignore` file in your project root to exclude files and improve performance:

```
# Build directories (performance critical)
build/**
target/**
out/**

# Dependencies (performance critical)
**/node_modules/**
.gradle/**

# Generated files
**/generated/**
*.generated.kt

# IDE and temp files
.idea/**
*.tmp
*.class
```

### Shell Script Wrapper

For convenience, you can use the included shell script:

```bash
./ktfmt-cli --write src/**/*.kt
```

## Versioning

This project uses composite versioning: `{cli-version}-ktfmt{ktfmt-version}`

Examples:
- `1.0.0-ktfmt0.47` - CLI version 1.0.0 with ktfmt 0.47
- `1.1.0-ktfmt0.47` - CLI version 1.1.0 with ktfmt 0.47

Check version:
```bash
java -jar ktfmt-cli.jar --version
```

## Dependencies

- [ktfmt](https://github.com/facebook/ktfmt) - The Kotlin code formatter

## Development

### Release Process

1. Update versions in `build.gradle.kts`:
   ```kotlin
   val cliVersion = "1.0.0"        // Update CLI version
   val ktfmtVersion = "0.47"       // Update ktfmt version if needed
   ```

2. Create and push tag:
   ```bash
   git tag v1.0.0-ktfmt0.47
   git push origin v1.0.0-ktfmt0.47
   ```

3. GitHub Actions automatically builds and releases the JAR.

### Testing

```bash
# Shell wrapper
./ktfmt-cli --help

# Direct JAR
java -jar build/libs/ktfmt-cli-*.jar --check src/**/*.kt
```

## License

This project is licensed under the same license as ktfmt (Apache License 2.0).