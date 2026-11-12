package com.urkaaaz.application.tutorial

data class TutorialProgress(
    val terminalStatuses: Map<String, TutorialStatus> = emptyMap(),
    val schemaVersion: Int = TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION,
    val catalogRevision: String = "",
)

enum class TutorialProgressFailure {
    STORAGE_UNAVAILABLE,
    INVALID_DATA,
    UNSUPPORTED_SCHEMA,
}

sealed interface TutorialProgressRead {
    data class Available(val progress: TutorialProgress) : TutorialProgressRead
    data class Failure(val reason: TutorialProgressFailure) : TutorialProgressRead
}

sealed interface TutorialProgressWrite {
    data object Saved : TutorialProgressWrite
    data class Failure(val reason: TutorialProgressFailure) : TutorialProgressWrite
}

interface TutorialProgressRepository {
    suspend fun load(validTutorialIds: Set<String>): TutorialProgressRead

    suspend fun markTerminal(
        tutorialId: String,
        status: TutorialStatus,
        schemaVersion: Int,
        catalogRevision: String,
    ): TutorialProgressWrite

    suspend fun reset(tutorialId: String? = null): TutorialProgressWrite
}
