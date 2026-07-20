package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.*
import org.example.minecraftmodcatelog.entities.*
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.example.minecraftmodcatelog.repositories.UserRepository
import org.example.minecraftmodcatelog.repositories.UserSubscriptionRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ModService(
    private val modrinthService: ModrinthService,
    private val modRepository: ModRepository,
    private val modVersionRepository: ModVersionRepository,
    private val modWriteService: ModWriteService,
    private val dependencyValidationService: DependencyValidationService,
    private val userRepository: UserRepository,
    private val userSubscriptionRepository: UserSubscriptionRepository
) {
    private fun getCurrentUser(): User {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw ResourceNotFoundException("No authentication context found")
        val email = authentication.name
        return userRepository.findByEmail(email)
            ?: throw ResourceNotFoundException("User not found with email: $email")
    }

    private fun isAdmin(user: User): Boolean {
        return user.role == UserRole.ROLE_ADMIN
    }

    private fun hasAccessToMod(user: User, mod: Mod): Boolean {
        return isAdmin(user) || userSubscriptionRepository.existsByUserAndMod(user, mod)
    }

    private fun verifyModAccess(slug: String): Mod {
        val user = getCurrentUser()
        val mod = modRepository.findBySlug(slug)
            ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
        if (!hasAccessToMod(user, mod)) {
            throw ResourceNotFoundException("Mod not found with slug: $slug")
        }
        return mod
    }

    @Transactional
    fun loadModBySlug(slug: String, forceUserAdded: Boolean = true): Mod {
        val existingBySlug = modRepository.findBySlug(slug)
        val mod = if (existingBySlug != null) {
            if (forceUserAdded && !existingBySlug.userAdded) {
                modWriteService.saveMod(
                    ModDTO(
                        modrinthProjectId = existingBySlug.modrinthProjectId,
                        slug = existingBySlug.slug,
                        title = existingBySlug.title,
                        description = existingBySlug.description,
                        author = existingBySlug.author,
                        iconUrl = existingBySlug.iconUrl,
                        lastSyncedAt = existingBySlug.lastSyncedAt
                    ), forceUserAdded = true
                )
            } else {
                existingBySlug
            }
        } else {
            val modDTO = modrinthService.searchProjectBySlug(slug)
            modWriteService.saveMod(modDTO, forceUserAdded)
        }

        if (forceUserAdded) {
            val user = getCurrentUser()
            if (!userSubscriptionRepository.existsByUserAndMod(user, mod)) {
                userSubscriptionRepository.save(UserSubscription(user = user, mod = mod))
            }
        }
        return mod
    }

    fun loadModByProjectId(projectId: String): Mod {
        val existing = modRepository.findByModrinthProjectId(projectId)
        if (existing != null) {
            return existing
        }
        val modDTO = modrinthService.searchProjectById(projectId)
        return modWriteService.saveMod(modDTO, forceUserAdded = false)
    }

    private fun createModVersion(mod: Mod, version: String, loader: Loader): ModVersion {
        val existingVersion = modVersionRepository.findByModAndVersionAndLoader(mod, version, loader)
        if (existingVersion != null) {
            return existingVersion
        }
        val modVersionDTO = modrinthService.searchProjectVersion(mod, version, loader)

        // Batch lookup existing dependency mods from DB
        val existingMods = modRepository.findAllByModrinthProjectIdIn(modVersionDTO.dependencies)
            .associateBy { it.modrinthProjectId }

        val dependencyMods = modVersionDTO.dependencies.map { depProjId ->
            existingMods[depProjId] ?: loadModByProjectId(depProjId)
        }.toSet()

        return modWriteService.saveModVersion(mod, version, loader, modVersionDTO, dependencyMods)
    }

    fun getOrCreateModVersion(slug: String, version: String, loader: Loader): ModVersionWithDependenciesDTO {
        val user = getCurrentUser()
        val mod = if (isAdmin(user)) {
            loadModBySlug(slug, forceUserAdded = false)
        } else {
            val existing = modRepository.findBySlug(slug)
                ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
            if (!userSubscriptionRepository.existsByUserAndMod(user, existing)) {
                throw ResourceNotFoundException("Mod not found with slug: $slug")
            }
            existing
        }
        val modVersion = createModVersion(mod, version, loader)
        return ModVersionWithDependenciesDTO(modVersion)
    }

    fun getAllMods(): List<Mod> {
        val user = getCurrentUser()
        return if (isAdmin(user)) {
            modRepository.findAll()
        } else {
            userSubscriptionRepository.findByUser(user).map { it.mod }
        }
    }

    fun existsBySlug(slug: String): Boolean {
        val mod = modRepository.findBySlug(slug) ?: return false
        val user = getCurrentUser()
        return hasAccessToMod(user, mod)
    }

    @Transactional
    fun loadModsAndDependencies(
        version: String,
        loader: Loader,
        onResolve: (String) -> Unit
    ): Pair<List<ModVersionWithoutDependenciesDTO>, List<String>> {
        val discoveredVersions = discoverModsAndDependenciesStreaming(version, loader, onResolve)
        val validationResults = dependencyValidationService.validateAndPersist(discoveredVersions, version, loader)

        val available = discoveredVersions
            .filter { validationResults[it.id] == ValidationState.VALID }
            .map { ModVersionWithoutDependenciesDTO(it) }

        val user = getCurrentUser()
        val rootMods = if (isAdmin(user)) {
            modRepository.findAllByUserAdded(true)
        } else {
            userSubscriptionRepository.findByUser(user).map { it.mod }
        }
        val unavailable = mutableListOf<String>()

        val discoveredMap = discoveredVersions.associateBy { it.mod.id }

        for (mod in rootMods) {
            val mv = discoveredMap[mod.id]
            if (mv == null || validationResults[mv.id] == ValidationState.INVALID) {
                unavailable.add(mod.title)
            }
        }

        return Pair(available, unavailable)
    }

    fun getModsByVersionAndLoaderStreaming(
        version: String,
        loader: Loader,
        onResolve: (String) -> Unit
    ): ModResolutionResultDTO {
        val result = loadModsAndDependencies(version, loader, onResolve)
        return ModResolutionResultDTO(result)
    }

    private fun discoverModsAndDependenciesStreaming(
        version: String,
        loader: Loader,
        onResolve: (String) -> Unit
    ): List<ModVersionNodeDTO> {
        val user = getCurrentUser()
        val rootMods = if (isAdmin(user)) {
            modRepository.findAllByUserAdded(true)
        } else {
            userSubscriptionRepository.findByUser(user).map { it.mod }
        }
        val queue: Queue<ModVersionNodeDTO> = LinkedList()
        val loadedProjectIds = mutableSetOf<String>()
        val discoveredVersions = mutableListOf<ModVersionNodeDTO>()

        for (mod in rootMods) {
            try {
                val mv = createModVersion(mod, version, loader)
                val node = ModVersionNodeDTO(mv)
                queue.add(node)
                loadedProjectIds.add(mod.modrinthProjectId)
                discoveredVersions.add(node)
                onResolve("${mod.title} ($version)")
            } catch (_: ResourceNotFoundException) {
                // Ignore missing versions during discovery
            }
        }

        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            // In our NodeDTO, dependencyProjectIds contains project IDs.
            // Let's resolve the actual database mods by these project IDs.
            val depMods = modRepository.findAllByModrinthProjectIdIn(curr.dependencyProjectIds)
            for (depMod in depMods) {
                if (depMod.modrinthProjectId !in loadedProjectIds) {
                    loadedProjectIds.add(depMod.modrinthProjectId)
                    try {
                        val depMv = createModVersion(depMod, version, loader)
                        val node = ModVersionNodeDTO(depMv)
                        queue.add(node)
                        discoveredVersions.add(node)
                        onResolve("${depMod.title} ($version)")
                    } catch (_: ResourceNotFoundException) {
                        // Ignore missing dependency versions during discovery
                    }
                }
            }
        }
        return discoveredVersions
    }


    fun getAllUserAddedMods(): List<Mod> {
        val user = getCurrentUser()
        return if (isAdmin(user)) {
            modRepository.findAllByUserAdded(true)
        } else {
            userSubscriptionRepository.findByUser(user).map { it.mod }
        }
    }

    @Transactional
    fun deleteUserAddedMod(id: UUID) {
        val user = getCurrentUser()
        val mod = modRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Mod not found with ID: $id")
        }
        val subscription = userSubscriptionRepository.findByUserAndMod(user, mod)
            ?: throw ResourceNotFoundException("Mod not found with ID: $id")
        userSubscriptionRepository.delete(subscription)
    }

    @Transactional
    fun deleteModBySlug(slug: String) {
        val mod = verifyModAccess(slug)
        deleteUserAddedMod(mod.id)
    }

    fun getDependencies(slug: String): List<Mod> {
        val mod = verifyModAccess(slug)
        return modRepository.findDependenciesByModSlug(mod.slug)
    }

    fun getDependents(slug: String): List<Mod> {
        val mod = verifyModAccess(slug)
        return modRepository.findDependentsByModSlug(mod.slug)
    }

    fun getRootMods(): List<Mod> {
        val user = getCurrentUser()
        val rootMods = modRepository.findRootMods()
        return if (isAdmin(user)) {
            rootMods
        } else {
            val subscribedMods = userSubscriptionRepository.findByUser(user).map { it.mod }.toSet()
            rootMods.filter { it in subscribedMods }
        }
    }

    @Transactional
    fun setUserAdded(id: UUID, userAdded: Boolean) {
        val user = getCurrentUser()
        val mod = modRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Mod not found with ID: $id")
        }
        if (userAdded) {
            if (!userSubscriptionRepository.existsByUserAndMod(user, mod)) {
                userSubscriptionRepository.save(UserSubscription(user = user, mod = mod))
            }
            if (!mod.userAdded) {
                mod.userAdded = true
                modRepository.save(mod)
            }
        } else {
            if (isAdmin(user)) {
                userSubscriptionRepository.deleteByMod(mod)
            } else {
                val subscription = userSubscriptionRepository.findByUserAndMod(user, mod)
                if (subscription != null) {
                    userSubscriptionRepository.delete(subscription)
                }
            }
        }
    }

    fun getDependencyGraph(version: String, loader: Loader): List<ModVersionDependencyGraphDTO> {
        val user = getCurrentUser()
        val allVersions = modVersionRepository.findAllByVersionAndLoaderAndValidationState(
            version = version,
            loader = loader,
            validationState = ValidationState.VALID
        )
        val filteredVersions = if (isAdmin(user)) {
            allVersions
        } else {
            val subscribedMods = userSubscriptionRepository.findByUser(user).map { it.mod }.toSet()
            allVersions.filter { it.mod in subscribedMods }
        }
        return filteredVersions.map { mv ->
            ModVersionDependencyGraphDTO(
                modName = mv.mod.title,
                dependencies = mv.dependencies.map { it.title }
            )
        }
    }
}
