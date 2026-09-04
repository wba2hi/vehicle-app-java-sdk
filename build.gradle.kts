/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.io.path.useLines
import kotlin.io.path.visitFileTree
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.eclipse.velocitas.version.SemanticVersion
import org.eclipse.velocitas.version.VERSION_FILE_DEFAULT_NAME
import org.eclipse.velocitas.version.VERSION_FILE_DEFAULT_PATH_KEY

/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

val versionDefaultPath = "$rootDir/$VERSION_FILE_DEFAULT_NAME"
rootProject.ext[VERSION_FILE_DEFAULT_PATH_KEY] = versionDefaultPath
val semanticVersion = SemanticVersion(versionDefaultPath)
version = semanticVersion.versionName
group = "org.eclipse.velocitas"

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    version
    alias(libs.plugins.gradle.nexus.publish.plugin)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false // activate all available (even unstable) rules.
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")
}

nexusPublishing {
    repositories {
        sonatype {
            username = System.getenv("ORG_OSSRH_USERNAME")
            password = System.getenv("ORG_OSSRH_PASSWORD")
        }
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        md.required.set(true)
    }

    parallel = true
    setSource(projectDir)
    include("**/*.kt", "**/*.kts")
    exclude("**/resources/**", "**/build/**", "**/node_modules/**", "**/cache/**")

    jvmTarget = "17"
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"

    setSource(projectDir)
    include("**/*.kt", "**/*.kts")
    exclude("**/resources/**", "**/build/**", "**/node_modules/**", "**/cache/**")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

subprojects {
    apply {
        from("$rootDir/dash.gradle.kts")
    }
    afterEvaluate {
        tasks.findByName("check")?.finalizedBy(rootProject.tasks.named("detekt"))
    }

    // see: https://kotest.io/docs/framework/tags.html#gradle
    tasks.withType<Test>().configureEach {
        val systemPropertiesMap = HashMap<String, Any>()
        System.getProperties().forEach { (key, value) ->
            systemPropertiesMap[key.toString()] = value.toString()
        }
        systemProperties = systemPropertiesMap

        failOnNoDiscoveredTests = false
    }

    // https://docs.gradle.org/current/userguide/dependency_locking.html
    dependencyLocking {
        lockAllConfigurations()
        lockFile = file("$projectDir/gradle.lockfile")
    }
}

@OptIn(ExperimentalPathApi::class)
tasks.register("mergeDashFiles") {
    description = "Merges Dash Files"
    group = "oss"

    dependsOn(
        subprojects.map { subproject ->
            subproject.tasks.getByName("createDashFile")
        },
    )

    val buildDir = layout.buildDirectory.asFile.get()
    val buildDirPath = Path.of(buildDir.path)

    doLast {
        val ossDir = buildDirPath.resolve("oss").createDirectories()
        val ossAllDir = ossDir.resolve("all").createDirectories()
        val ossDependenciesFile = ossAllDir.resolve("all-dependencies.txt")
        ossDependenciesFile.deleteIfExists()
        ossDependenciesFile.createFile()

        val sortedLinesSet = sortedSetOf<String>()
        ossDir.visitFileTree {
            onVisitFile { file, _ ->
                if (file.name != "dependencies.txt") return@onVisitFile FileVisitResult.CONTINUE

                file.useLines {
                    sortedLinesSet.addAll(it)
                }

                FileVisitResult.CONTINUE
            }
        }

        val bufferedWriter = ossDependenciesFile.bufferedWriter()
        bufferedWriter.use { writer ->
            sortedLinesSet.forEach { line ->
                writer.write(line + System.lineSeparator())
            }
        }
    }
}

// Resolve and lock all configurations of all submodules.
// Each subproject resolves its own configurations to avoid cross-project locking violations
// when Gradle parallel execution is enabled (org.gradle.parallel=true).
// https://docs.gradle.org/current/userguide/dependency_locking.html
val resolveAndLockAllSubprojects = subprojects.map { subproject ->
    subproject.tasks.register("resolveAndLockAll") {
        group = "dependency locking"
        description =
            "Resolves and locks all configurations of ${subproject.name}. Must be run with the --write-locks flag."

        notCompatibleWithConfigurationCache("Filters configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "$path must be run from the command line with the `--write-locks` flag"
            }
        }
        doLast {
            subproject.configurations
                .filter { it.isCanBeResolved }
                .forEach { config ->
                    try {
                        config.resolve()
                    } catch (e: ResolveException) {
                        logger.info("Skipping resolution for configuration '${config.name}': ${e.message}")
                    }
                }
        }
    }
}

tasks.register("resolveAndLockAll") {
    group = "dependency locking"
    description = "Resolves and locks all configurations of all submodules. Must be run with the --write-locks flag."

    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "$path must be run from the command line with the `--write-locks` flag"
        }
    }
    // Also resolve root project configurations
    doLast {
        configurations
            .filter { it.isCanBeResolved }
            .forEach { config ->
                try {
                    config.resolve()
                } catch (e: ResolveException) {
                    logger.info("Skipping resolution for configuration '${config.name}': ${e.message}")
                }
            }
    }
    dependsOn(resolveAndLockAllSubprojects)
}
