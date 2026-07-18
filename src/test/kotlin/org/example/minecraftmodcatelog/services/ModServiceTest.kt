package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.exceptions.DependencyVersionNotFoundException
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.Instant
import java.util.*

class ModServiceTest {

    private val modrinthService = mock(ModrinthService::class.java)
    private val modRepository = mock(ModRepository::class.java)
    private val modVersionRepository = mock(ModVersionRepository::class.java)
    private val modWriteService = mock(ModWriteService::class.java)
    private val dependencyValidationService = mock(DependencyValidationService::class.java)

    private val modService = ModService(
        modrinthService,
        modRepository,
        modVersionRepository,
        modWriteService,
        dependencyValidationService
    )

    @Test
    fun `loadModsAndDependencies throws DependencyVersionNotFoundException when a dependency version is missing`() {
        // Arrange
        val version = "1.20.1"
        val loader = Loader.FABRIC

        val mainMod = Mod(
            id = UUID.randomUUID(),
            modrinthProjectId = "main-mod-id",
            slug = "main-mod",
            title = "Main Mod",
            description = "Main",
            author = "Author",
            iconUrl = "icon",
            userAdded = true,
            lastSyncedAt = Instant.now()
        )

        val depMod = Mod(
            id = UUID.randomUUID(),
            modrinthProjectId = "dep-mod-id",
            slug = "dep-mod",
            title = "Dependency Mod",
            description = "Dep",
            author = "Author",
            iconUrl = "icon",
            userAdded = false,
            lastSyncedAt = Instant.now()
        )

        // Set up mock version behavior for mainMod
        val mainVersion = ModVersion(
            id = UUID.randomUUID(),
            version = version,
            loader = loader,
            downloadUrl = "http://main.url",
            mod = mainMod,
            dependencies = mutableSetOf(depMod)
        )

        // Mock database and modrinth queries
        `when`(modRepository.findAllByUserAdded(true)).thenReturn(mutableListOf(mainMod))
        
        // Mock createModVersion for mainMod
        `when`(modVersionRepository.findByModAndVersionAndLoader(mainMod, version, loader))
            .thenReturn(mainVersion)

        // Mock createModVersion for depMod to fail (dependency version is missing)
        `when`(modVersionRepository.findByModAndVersionAndLoader(depMod, version, loader))
            .thenReturn(null)
        `when`(modrinthService.searchProjectVersion(depMod, version, loader))
            .thenThrow(ResourceNotFoundException("No matching version found"))

        // Mock validation service
        `when`(dependencyValidationService.validateAndPersist(listOf(mainVersion), version, loader))
            .thenReturn(mapOf(mainVersion.id to org.example.minecraftmodcatelog.entities.ValidationState.INVALID))

        // Act
        val (workingDtos, missingDependencies) = modService.loadModsAndDependencies(version, loader)

        // Assert: Root mod 'Main Mod' depends on missing dependency 'Dependency Mod', making it invalid.
        // It should appear in unavailable, and workingDtos should be empty.
        assertEquals(1, missingDependencies.size)
        assertEquals("Main Mod", missingDependencies[0])
        assertTrue(workingDtos.isEmpty())
    }
}
