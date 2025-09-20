plugins {
  kotlin("jvm") version "2.2.0"
  application
  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("com.ncorti.ktfmt.gradle") version "0.24.0"
}

repositories { mavenCentral() }

// Version configuration from gradle.properties
val cliVersion = providers.gradleProperty("cliVersion")
val ktfmtVersion = providers.gradleProperty("ktfmtVersion")
val fullVersion = cliVersion.zip(ktfmtVersion) { cli, ktfmt -> "$cli-ktfmt$ktfmt" }

dependencies {
  implementation(kotlin("stdlib"))
  implementation(ktfmtVersion.map { "com.facebook:ktfmt:$it" })
  implementation("com.github.ajalt.clikt:clikt:4.4.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("commons-codec:commons-codec:1.18.0")

  testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
  testImplementation("io.kotest:kotest-assertions-core:5.8.0")
  testImplementation("io.kotest:kotest-property:5.8.0")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("com.google.jimfs:jimfs:1.3.0")
}

application { mainClass.set("io.github.ukarlsson.ktfmtcli.AppKt") }

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
  archiveBaseName.set("ktfmt-cli")
  archiveClassifier.set("")
  archiveVersion.set(fullVersion)
}

// Pass version to compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") }
}

// Create version properties file
tasks.register("generateVersionProperties") {
  val outputDir = layout.buildDirectory.dir("resources/main")
  outputs.dir(outputDir)

  // Make the task configuration cache compatible by capturing values as inputs
  inputs.property("fullVersion", fullVersion)
  inputs.property("ktfmtVersion", ktfmtVersion)
  inputs.property("cliVersion", cliVersion)

  doLast {
    val versionFile = outputDir.get().asFile.resolve("version.properties")
    versionFile.parentFile.mkdirs()
    val fullVersionValue = inputs.properties["fullVersion"]
    val ktfmtVersionValue = inputs.properties["ktfmtVersion"]
    val cliVersionValue = inputs.properties["cliVersion"]
    versionFile.writeText("version=$fullVersionValue\nktfmt.version=$ktfmtVersionValue\ncli.version=$cliVersionValue")
  }
}

tasks.named("processResources") { dependsOn("generateVersionProperties") }

// Configure test task to use JUnit 5
// Configure ktfmt
ktfmt {
  googleStyle()
  maxWidth = 120
}

tasks.withType<Test> {
  useJUnitPlatform()

  // Output test results to stdout instead of HTML reports
  testLogging {
    events("passed", "skipped", "failed", "standardOut", "standardError")
    showExceptions = true
    showCauses = true
    showStackTraces = true
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
