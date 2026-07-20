package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.example.minecraftmodcatelog.repositories.UserRepository
import org.example.minecraftmodcatelog.repositories.UserSubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.*

class ModServiceTest {

    private val modrinthService = mock(ModrinthService::class.java)
    private val modRepository = mock(ModRepository::class.java)
    private val modVersionRepository = mock(ModVersionRepository::class.java)
    private val modWriteService = mock(ModWriteService::class.java)
    private val dependencyValidationService = mock(DependencyValidationService::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val userSubscriptionRepository = mock(UserSubscriptionRepository::class.java)

    private val modService = ModService(
        modrinthService,
        modRepository,
        modVersionRepository,
        modWriteService,
        dependencyValidationService,
        userRepository,
        userSubscriptionRepository
    )

    private val testUser = org.example.minecraftmodcatelog.entities.User(
        email = "test@example.com",
        password = "password",
        role = org.example.minecraftmodcatelog.entities.UserRole.ROLE_ADMIN
    )

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(
            "test@example.com", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        SecurityContextHolder.getContext().authentication = auth
        `when`(userRepository.findByEmail("test@example.com")).thenReturn(testUser)
    }

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
        val mainNode = org.example.minecraftmodcatelog.dto.ModVersionNodeDTO(mainVersion)
        `when`(dependencyValidationService.validateAndPersist(listOf(mainNode), version, loader))
            .thenReturn(mapOf(mainVersion.id to org.example.minecraftmodcatelog.entities.ValidationState.INVALID))

        // Act
        val (workingDtos, missingDependencies) = modService.loadModsAndDependencies(version, loader, onResolve = {})

        // Assert: Root mod 'Main Mod' depends on missing dependency 'Dependency Mod', making it invalid.
        // It should appear in unavailable, and workingDtos should be empty.
        assertEquals(1, missingDependencies.size)
        assertEquals("Main Mod", missingDependencies[0])
        assertTrue(workingDtos.isEmpty())
    }

    @Test
    fun `getModsByVersionAndLoaderStreaming resolves correctly and fires onResolve callback`() {
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

        val mainVersion = ModVersion(
            id = UUID.randomUUID(),
            version = version,
            loader = loader,
            downloadUrl = "http://main.url",
            mod = mainMod,
            dependencies = mutableSetOf()
        )

        `when`(modRepository.findAllByUserAdded(true)).thenReturn(mutableListOf(mainMod))
        `when`(modVersionRepository.findByModAndVersionAndLoader(mainMod, version, loader))
            .thenReturn(mainVersion)
        val mainNode = org.example.minecraftmodcatelog.dto.ModVersionNodeDTO(mainVersion)
        `when`(dependencyValidationService.validateAndPersist(listOf(mainNode), version, loader))
            .thenReturn(mapOf(mainVersion.id to org.example.minecraftmodcatelog.entities.ValidationState.VALID))

        val resolvedList = mutableListOf<String>()
        val result = modService.getModsByVersionAndLoaderStreaming(version, loader) { resolved ->
            resolvedList.add(resolved)
        }

        assertEquals(1, resolvedList.size)
        assertEquals("Main Mod (1.20.1)", resolvedList[0])
        assertEquals(1, result.available.size)
        assertEquals("Main Mod", result.available[0].modName)
    }

    @Test
    fun `getAllMods for regular user returns only subscribed mods`() {
        // Arrange
        val regularROLEUser = org.example.minecraftmodcatelog.entities.User(
            email = "regular@example.com",
            password = "password",
            role = org.example.minecraftmodcatelog.entities.UserRole.ROLE_USER
        )
        val auth = UsernamePasswordAuthenticationToken(
            "regular@example.com", null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        SecurityContextHolder.getContext().authentication = auth
        `when`(userRepository.findByEmail("regular@example.com")).thenReturn(regularROLEUser)

        val mod = Mod(
            id = UUID.randomUUID(),
            modrinthProjectId = "my-subscribed-mod",
            slug = "my-mod",
            title = "My Mod",
            description = "My Subscribed Mod",
            author = "Author",
            iconUrl = "icon",
            userAdded = true,
            lastSyncedAt = Instant.now()
        )
        val subscription = org.example.minecraftmodcatelog.entities.UserSubscription(
            user = regularROLEUser,
            mod = mod
        )
        `when`(userSubscriptionRepository.findByUser(regularROLEUser)).thenReturn(listOf(subscription))

        // Act
        val result = modService.getAllMods()

        // Assert
        assertEquals(1, result.size)
        assertEquals("My Mod", result[0].title)
    }
}
