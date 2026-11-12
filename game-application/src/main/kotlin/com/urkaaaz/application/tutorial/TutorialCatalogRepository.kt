package com.urkaaaz.application.tutorial

import java.io.IOException
import java.io.InputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface TutorialCatalogRead {
    data class Available(val catalog: TutorialCatalog) : TutorialCatalogRead
    data class Failure(val reason: TutorialCatalogFailure) : TutorialCatalogRead
}

enum class TutorialCatalogFailure {
    RESOURCE_UNAVAILABLE,
    INVALID_JSON,
    INVALID_CATALOG,
    STORAGE_UNAVAILABLE,
}

fun interface TutorialCatalogRepository {
    fun load(): TutorialCatalogRead

    companion object {
        fun bundled(
            resourceName: String = RESOURCE_NAME,
            resourceLoader: (String) -> InputStream? = { name ->
                TutorialCatalogRepository::class.java.classLoader?.getResourceAsStream(name)
            },
        ): TutorialCatalogRepository = TutorialCatalogRepository {
            val input = resourceLoader(resourceName)
                ?: return@TutorialCatalogRepository TutorialCatalogRead.Failure(
                    TutorialCatalogFailure.RESOURCE_UNAVAILABLE,
                )
            input.use {
                try {
                    val content = it.readBytes().decodeToString()
                    val catalog = json.decodeFromString<TutorialCatalog>(content)
                    val registryIssues =
                        TutorialCatalogRegistrationValidator.validate(catalog)
                    if (registryIssues.isNotEmpty()) {
                        throw TutorialCatalogValidationException(registryIssues)
                    }
                    TutorialCatalogRead.Available(catalog)
                } catch (_: IOException) {
                    TutorialCatalogRead.Failure(TutorialCatalogFailure.STORAGE_UNAVAILABLE)
                } catch (_: SerializationException) {
                    TutorialCatalogRead.Failure(TutorialCatalogFailure.INVALID_JSON)
                } catch (_: TutorialCatalogValidationException) {
                    TutorialCatalogRead.Failure(TutorialCatalogFailure.INVALID_CATALOG)
                }
            }
        }

        const val RESOURCE_NAME = "tutorial/tutorial-catalog.json"

        private val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}
