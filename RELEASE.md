# Release Instructions

## Creating a New Release

### 1. Prepare the Release

1. **Update version numbers** in relevant files:
   - Update download URLs in README.md if needed
   - Ensure version.properties has correct version

2. **Run final tests**:
   ```bash
   ./gradlew test
   ./gradlew build
   ```

3. **Commit and push all changes**:
   ```bash
   git add .
   git commit -m "Prepare release v0.0.1"
   git push origin main
   ```

### 2. Create Release Tag

```bash
# Create annotated tag
git tag -a v0.0.1 -m "Initial release v0.0.1

## Features
- Format Kotlin files using ktfmt
- Support for multiple formatting styles (Meta, Google, KotlinLang)
- Glob pattern support with optimized directory traversal
- .ktfmtignore file support
- Parallel processing with configurable concurrency
- Debug timing information
- CI/CD integration with check mode"

# Push tag to trigger release
git push origin v0.0.1
```

### 3. Create GitHub Release

The GitHub Actions workflow should automatically:
1. Build the JAR
2. Create a GitHub release
3. Upload the JAR as a release asset

If manual creation is needed:
```bash
gh release create v0.0.1 \
  --title "ktfmt-cli v0.0.1" \
  --notes "Initial release with core formatting features" \
  build/libs/ktfmt-cli-*.jar
```

### 4. Update README

Update the download URL in README.md to point to the new release:
```markdown
wget https://github.com/ukarlsson/ktfmt-cli/releases/latest/download/ktfmt-cli-0.0.1.jar
```

## Release Checklist

- [ ] All tests pass
- [ ] Code is formatted and linted
- [ ] README.md is up to date
- [ ] Version numbers are correct
- [ ] Tag is created with proper message
- [ ] GitHub release is created
- [ ] JAR is uploaded to release
- [ ] Download links are updated

## Version Numbering

We follow semantic versioning (semver):
- `MAJOR.MINOR.PATCH` (e.g., 1.2.3)
- For pre-1.0: `0.MINOR.PATCH` (e.g., 0.1.0, 0.0.1)

Current release: **v0.0.1** (Initial release)