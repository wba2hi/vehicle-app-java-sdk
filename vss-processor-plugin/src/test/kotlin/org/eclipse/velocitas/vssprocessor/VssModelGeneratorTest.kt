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

package org.eclipse.velocitas.vssprocessor

import java.io.File
import kotlin.io.path.createTempDirectory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.eclipse.velocitas.TestResourceFile
import org.eclipse.velocitas.kotest.Unit
import org.gradle.api.logging.Logger

class VssModelGeneratorTest : BehaviorSpec({
    tags(Unit)

    given("A VssModelGenerator with VSS 6.0 definitions") {
        val tempDir = createTempDirectory("vss_generator_test").toFile()
        val logger = mockk<Logger>(relaxed = true)
        val generator = VssModelGenerator(tempDir, logger)

        afterSpec {
            tempDir.deleteRecursively()
        }

        `when`("generating models for vss_rel_6.0.yaml") {
            val vss6File = TestResourceFile("yaml/vss_rel_6.0.yaml")
            generator.generate(setOf(vss6File))

            then("it should generate VssPositionOffsetTargetMode.kt with correct nested parent import") {
                val generatedFile = File(
                    tempDir,
                    "build/generated/vss/kotlin/org/eclipse/velocitas/vss/VssPositionOffsetTargetMode.kt",
                )
                generatedFile.exists() shouldBe true
                val content = generatedFile.readText()
                content shouldContain "import org.eclipse.velocitas.vss.VssSteering.VssAxle.VssRow1"
            }

            then("it should generate VssSteerAngleVelocityTarget.kt with correct nested parent import") {
                val generatedFile = File(
                    tempDir,
                    "build/generated/vss/kotlin/org/eclipse/velocitas/vss/VssSteerAngleVelocityTarget.kt",
                )
                generatedFile.exists() shouldBe true
                val content = generatedFile.readText()
                content shouldContain "import org.eclipse.velocitas.vss.VssSteering.VssAxle.VssRow2"
            }
        }
    }
})

