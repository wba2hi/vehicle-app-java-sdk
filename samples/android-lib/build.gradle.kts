
plugins {
    id("com.android.library")
    id("org.eclipse.velocitas.vss-processor-plugin") // version <VERSION>
}

vssProcessor {
    searchPath = "vss"
}

android {
    namespace = "com.example.android.lib"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sdk"))

    implementation(libs.kotlinx.coroutines.android)
}
