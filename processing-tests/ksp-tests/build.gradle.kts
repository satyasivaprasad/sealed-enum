plugins {
    id("symbol-processing") version Versions.ksp
    idea
}

repositories {
    google()
}

/**
 * Swap to `true` to allow debugging `processor-tests`
 */
val debugProcessor = false
if (!debugProcessor) {
    sourceSets {
        test {
            java {
                srcDir("$rootDir/processing-tests/common/test/java")
                srcDir("$rootDir/processing-tests/common/test/kotlin")
            }
        }
    }
}

dependencies {
    testImplementation(Dependencies.junit)
    testImplementation(Dependencies.kotlinCompileTesting)
    testImplementation(kotlin("reflect"))
    testImplementation(project(":runtime"))
    testImplementation(Dependencies.kotlinCompilerEmbeddable)
    testImplementation(Dependencies.kotlinCompileTestingKsp)
    testImplementation(Dependencies.ksp)
    testImplementation(Dependencies.kspApi)
    testImplementation(project(":ksp"))
    ksp(project(":ksp"))
}
