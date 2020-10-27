plugins {
    kotlin("kapt")
}

repositories {
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":runtime"))
    implementation(project(":processing-common"))
    implementation(Dependencies.kotlinCompilerEmbeddable)
    implementation(Dependencies.kotlinPoet)
    compileOnly(Dependencies.kspApi)
    autoService()
}

kapt {
    includeCompileClasspath = false
}
