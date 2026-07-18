package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.*

class DependencyValidationServiceTest {

    private val modVersionRepository = mock(ModVersionRepository::class.java)
    private val validationService = DependencyValidationService(modVersionRepository)

    private fun createDummyMod(title: String, userAdded: Boolean = false): Mod {
        return Mod(
            id = UUID.randomUUID(),
            modrinthProjectId = title.lowercase() + "-id",
            slug = title.lowercase(),
            title = title,
            description = "Desc",
            author = "Author",
            iconUrl = "icon",
            userAdded = userAdded,
            lastSyncedAt = Instant.now()
        )
    }

    private fun createDummyVersion(
        mod: Mod,
        downloadUrl: String = "http://download.url",
        dependencies: Set<Mod> = emptySet(),
        version: String = "1.20.1",
        loader: Loader = Loader.FABRIC
    ): ModVersion {
        return ModVersion(
            id = UUID.randomUUID(),
            version = version,
            loader = loader,
            downloadUrl = downloadUrl,
            mod = mod,
            dependencies = dependencies.toMutableSet(),
            validationState = ValidationState.UNKNOWN
        )
    }

    @Test
    fun `valid dependency tree`() {
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val mvB = createDummyVersion(modB)
        val mvA = createDummyVersion(modA, dependencies = setOf(modB))

        val result = validationService.validateAndPersist(listOf(mvA, mvB), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.VALID, result[mvA.id])
        assertEquals(ValidationState.VALID, result[mvB.id])
    }

    @Test
    fun `missing dependency`() {
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val mvA = createDummyVersion(modA, dependencies = setOf(modB))

        // Note: mvB is not in the discovered versions, nor in the DB
        `when`(modVersionRepository.findByModAndVersionAndLoader(modB, "1.20.1", Loader.FABRIC))
            .thenReturn(null)

        val result = validationService.validateAndPersist(listOf(mvA), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.INVALID, result[mvA.id])
    }

    @Test
    fun `missing download URL`() {
        val modA = createDummyMod("ModA")
        val mvA = createDummyVersion(modA, downloadUrl = "")

        val result = validationService.validateAndPersist(listOf(mvA), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.INVALID, result[mvA.id])
    }

    @Test
    fun `multi-level invalid propagation`() {
        // A -> B -> C (C has missing download URL)
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val modC = createDummyMod("ModC")

        val mvC = createDummyVersion(modC, downloadUrl = "")
        val mvB = createDummyVersion(modB, dependencies = setOf(modC))
        val mvA = createDummyVersion(modA, dependencies = setOf(modB))

        val result = validationService.validateAndPersist(listOf(mvA, mvB, mvC), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.INVALID, result[mvC.id])
        assertEquals(ValidationState.INVALID, result[mvB.id])
        assertEquals(ValidationState.INVALID, result[mvA.id])
    }

    @Test
    fun `shared dependency`() {
        // A -> C, B -> C
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val modC = createDummyMod("ModC")

        val mvC = createDummyVersion(modC)
        val mvB = createDummyVersion(modB, dependencies = setOf(modC))
        val mvA = createDummyVersion(modA, dependencies = setOf(modC))

        val result = validationService.validateAndPersist(listOf(mvA, mvB, mvC), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.VALID, result[mvA.id])
        assertEquals(ValidationState.VALID, result[mvB.id])
        assertEquals(ValidationState.VALID, result[mvC.id])
    }

    @Test
    fun `cyclic dependencies`() {
        // A -> B -> A
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")

        val mvA = createDummyVersion(modA)
        val mvB = createDummyVersion(modB, dependencies = setOf(modA))
        mvA.dependencies.add(modB)

        val result = validationService.validateAndPersist(listOf(mvA, mvB), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.INVALID, result[mvA.id])
        assertEquals(ValidationState.INVALID, result[mvB.id])
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKotlin(): T {
        any<T>()
        return null as T
    }

    @Test
    fun `memoization - shared dependency validated only once`() {
        // A -> C, B -> C
        // We will monitor lookup/repository calls to verify memoization.
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val modC = createDummyMod("ModC")

        val mvC = createDummyVersion(modC)
        val mvB = createDummyVersion(modB, dependencies = setOf(modC))
        val mvA = createDummyVersion(modA, dependencies = setOf(modC))

        // We validate A first, which will validate C and cache it.
        // Then we validate B, which depends on C. C should be resolved from discoveredMap/cache, not re-evaluated.
        // We can verify that save is only called once per modVersion in the repository because its state is updated once.
        // But more specifically, let's verify finding in DB is never called since we pass all of them in discovered list.
        val result = validationService.validateAndPersist(listOf(mvA, mvB, mvC), "1.20.1", Loader.FABRIC)

        assertEquals(ValidationState.VALID, result[mvA.id])
        assertEquals(ValidationState.VALID, result[mvB.id])
        assertEquals(ValidationState.VALID, result[mvC.id])

        // Verify finding B or C by DB query is 0 times since we supplied them in discoveredVersions
        verify(modVersionRepository, never()).findByModAndVersionAndLoader(anyKotlin(), anyKotlin(), anyKotlin())
    }
}
