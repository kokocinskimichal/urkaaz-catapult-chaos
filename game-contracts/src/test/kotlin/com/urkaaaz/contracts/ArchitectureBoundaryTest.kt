package com.urkaaaz.contracts

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    private val repositoryRoot: Path = Paths.get(
        requireNotNull(System.getProperty("urkaaz.repositoryRoot")) {
            "Gradle must provide the repository root for architecture tests"
        },
    )

    @Test
    fun coreModulesDoNotImportAndroidOrUiCode() {
        val forbiddenImport = Regex(
            """^\s*import\s+(?:android|androidx|com\.urkaaaz\.(?:android|ui|persistence))(?:\.|\s|$)""",
        )
        val violations = sourceViolations(
            modules = CORE_MODULES,
            pattern = forbiddenImport,
        )

        assertTrue(
            violations.isEmpty(),
            "Core modules must not import Android/UI code:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun productionModuleDependenciesMatchTheApprovedDirection() {
        val includedModules = Regex("\"(:[^\"]+)\"")
            .findAll(Files.readString(repositoryRoot.resolve("settings.gradle.kts")))
            .map { it.groupValues[1].removePrefix(":") }
            .toSet()
        assertEquals(MODULES.toSet(), includedModules)

        val dependencyPattern = Regex(
            """^\s*(?:implementation|api|compileOnly|runtimeOnly)\s*\(\s*project\(":(.*?)"\)\s*\)""",
            RegexOption.MULTILINE,
        )
        val actualDependencies = MODULES.associateWith { module ->
            val buildFile = repositoryRoot.resolve("$module/build.gradle.kts")
            dependencyPattern.findAll(Files.readString(buildFile))
                .map { it.groupValues[1] }
                .toSet()
        }

        assertEquals(APPROVED_DEPENDENCIES, actualDependencies)
    }

    @Test
    fun productionCodeDoesNotInferTeamsFromIdentifierText() {
        val teamIdHeuristic = Regex(
            """(?:startsWith|contains)\s*\(\s*["'](?:red|blue|ai)["']""",
            RegexOption.IGNORE_CASE,
        )
        val violations = sourceViolations(
            modules = listOf("game-application", "android-app"),
            pattern = teamIdHeuristic,
        )

        assertTrue(
            violations.isEmpty(),
            "Team identity must come from typed assignments:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun matchViewModelDoesNotDependOnSimulationLoggingPorts() {
        val viewModel = repositoryRoot.resolve(
            "android-app/src/main/java/com/urkaaaz/ui/MatchViewModel.kt",
        )

        assertFalse(
            Files.readString(viewModel).contains("com.urkaaaz.simulation"),
            "MatchViewModel must not depend on simulation logging implementation types",
        )
    }

    private fun sourceViolations(
        modules: List<String>,
        pattern: Regex,
    ): List<String> = buildList {
        modules.forEach { module ->
            val sourceRoot = repositoryRoot.resolve("$module/src/main")
            if (!Files.exists(sourceRoot)) return@forEach
            Files.walk(sourceRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .forEach { source ->
                        Files.readAllLines(source).forEachIndexed { index, line ->
                            if (pattern.containsMatchIn(line)) {
                                add(
                                    "${repositoryRoot.relativize(source)}:${index + 1}: $line",
                                )
                            }
                        }
                    }
            }
        }
    }

    private companion object {
        val CORE_MODULES = listOf(
            "game-contracts",
            "game-domain",
            "game-simulation",
            "game-campaign",
            "game-ai",
            "game-application",
            "game-assets",
        )

        val MODULES = CORE_MODULES + "android-app"

        val APPROVED_DEPENDENCIES = mapOf(
            "game-contracts" to emptySet(),
            "game-domain" to setOf("game-contracts"),
            "game-simulation" to setOf("game-contracts", "game-domain"),
            "game-campaign" to setOf("game-contracts", "game-domain", "game-assets"),
            "game-ai" to setOf("game-contracts", "game-domain"),
            "game-application" to setOf(
                "game-contracts",
                "game-campaign",
                "game-domain",
                "game-simulation",
                "game-ai",
            ),
            "game-assets" to setOf("game-contracts"),
            "android-app" to setOf(
                "game-assets",
                "game-application",
                "game-campaign",
                "game-contracts",
                "game-simulation",
            ),
        )
    }
}
