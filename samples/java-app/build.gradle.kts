import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("application")
    kotlin("jvm")
    id("org.eclipse.velocitas.vss-processor-plugin") // version <VERSION>
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sdk"))

    implementation(libs.kotlinx.coroutines.core.jvm)
}

vssProcessor {
    searchPath = "$projectDir/vss"
}
