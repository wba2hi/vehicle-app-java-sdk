plugins {
    id("com.android.application")
    id("org.eclipse.velocitas.vss-processor-plugin") // version <VERSION>
}

vssProcessor {
    searchPath = "vss"
}

android {
    namespace = "com.example.android.app"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
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
