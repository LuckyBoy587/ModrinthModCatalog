package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionNodeDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.*

class DependencyValidationServiceTest {

    private val modVersionRepository = mock(ModVersionRepository::class.java)
    private val modRepository = mock(ModRepository::class.java)
    private val validationService = DependencyValidationService(modVersionRepository, modRepository)

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

        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modb-id"))).thenReturn(list(modB))
        `when`(modRepository.findAllByModrinthProjectIdIn(emptySet())).thenReturn(emptyList())

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA), ModVersionNodeDTO(mvB)), "1.20.1", Loader.FABRIC
        )

        assertEquals(ValidationState.VALID, result[mvA.id])
        assertEquals(ValidationState.VALID, result[mvB.id])
    }

    @Test
    fun `missing dependency`() {
        val modA = createDummyMod("ModA")
        val modB = createDummyMod("ModB")
        val mvA = createDummyVersion(modA, dependencies = setOf(modB))

        // Mock resolving dependency mod from database -> empty since missing
        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modb-id"))).thenReturn(emptyList())

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA)), "1.20.1", Loader.FABRIC
        )

        assertEquals(ValidationState.INVALID, result[mvA.id])
    }

    @Test
    fun `missing download URL`() {
        val modA = createDummyMod("ModA")
        val mvA = createDummyVersion(modA, downloadUrl = "")

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA)), "1.20.1", Loader.FABRIC
        )

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

        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modb-id"))).thenReturn(list(modB))
        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modc-id"))).thenReturn(list(modC))
        `when`(modRepository.findAllByModrinthProjectIdIn(emptySet())).thenReturn(emptyList())

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA), ModVersionNodeDTO(mvB), ModVersionNodeDTO(mvC)),
            "1.20.1", Loader.FABRIC
        )

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

        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modc-id"))).thenReturn(list(modC))
        `when`(modRepository.findAllByModrinthProjectIdIn(emptySet())).thenReturn(emptyList())

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA), ModVersionNodeDTO(mvB), ModVersionNodeDTO(mvC)),
            "1.20.1", Loader.FABRIC
        )

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

        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("moda-id"))).thenReturn(list(modA))
        `when`(modRepository.findAllByModrinthProjectIdIn(setOf("modb-id"))).thenReturn(list(modB))

        val result = validationService.validateAndPersist(
            listOf(ModVersionNodeDTO(mvA), ModVersionNodeDTO(mvB)), "1.20.1", Loader.FABRIC
        )

        assertEquals(ValidationState.INVALID, result[mvA.id])
        assertEquals(ValidationState.INVALID, result[mvB.id])
    }

    private fun <T> list(item: T): List<T> {
        return listOf(item)
    }
}

