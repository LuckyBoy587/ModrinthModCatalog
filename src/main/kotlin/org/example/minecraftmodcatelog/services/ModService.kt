package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.*
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import org.example.minecraftmodcatelog.exceptions.InvalidStateException
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ModService(
    private val modrinthService: ModrinthService,
    private val modRepository: ModRepository,
    private val modVersionRepository: ModVersionRepository,
    private val modWriteService: ModWriteService,
    private val dependencyValidationService: DependencyValidationService
) {
    fun loadModBySlug(slug: String, forceUserAdded: Boolean = true): Mod {
        val existingBySlug = modRepository.findBySlug(slug)
        if (existingBySlug != null) {
            if (forceUserAdded && !existingBySlug.userAdded) {
                return modWriteService.saveMod(
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
            }
            return existingBySlug
        }

        val modDTO = modrinthService.searchProjectBySlug(slug)
        return modWriteService.saveMod(modDTO, forceUserAdded)
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
        val mod = loadModBySlug(slug, forceUserAdded = false)
        val modVersion = createModVersion(mod, version, loader)
        return ModVersionWithDependenciesDTO(modVersion)
    }

    fun getAllMods(): List<Mod> {
        return modRepository.findAll()
    }

    fun existsBySlug(slug: String): Boolean {
        return modRepository.findBySlug(slug) != null
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

        val rootMods = modRepository.findAllByUserAdded(true)
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
        val rootMods = modRepository.findAllByUserAdded(true)
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
        return modRepository.findAllByUserAdded(true)
    }

    fun deleteUserAddedMod(id: UUID) {
        val mod = modRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Mod not found with ID: $id")
        }
        if (!mod.userAdded) {
            throw InvalidStateException("Only user-added mods can be deleted")
        }

        val modsToDelete = mutableSetOf<Mod>()
        val queue = ArrayDeque<Mod>()
        queue.add(mod)
        modsToDelete.add(mod)

        while (queue.isNotEmpty()) {
            val currentMod = queue.poll()
            val dependencyMods = currentMod.versions.flatMap { v -> v.dependencies }.toSet()

            for (depMod in dependencyMods) {
                if (modsToDelete.contains(depMod)) {
                    continue
                }

                val otherDependentsCount = modVersionRepository.countOtherDependents(depMod, modsToDelete)

                if (otherDependentsCount == 0L) {
                    modsToDelete.add(depMod)
                    queue.add(depMod)
                }
            }
        }

        val versionsToDelete = modsToDelete.flatMap { it.versions }.toList()
        val versionIds = versionsToDelete.map { it.id }

        if (versionIds.isNotEmpty()) {
            modVersionRepository.deleteDependenciesByVersionIds(versionIds)
        }

        for (version in versionsToDelete) {
            version.dependencies.clear()
        }
        for (m in modsToDelete) {
            m.versions.clear()
        }

        modVersionRepository.deleteAllInBatch(versionsToDelete)
        modRepository.deleteAllInBatch(modsToDelete)
    }

    @Transactional
    fun deleteModBySlug(slug: String) {
        val mod = modRepository.findBySlug(slug) ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
        deleteUserAddedMod(mod.id)
    }

    fun getDependencies(slug: String): List<Mod> {
        modRepository.findBySlug(slug) ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
        return modRepository.findDependenciesByModSlug(slug)
    }

    fun getDependents(slug: String): List<Mod> {
        modRepository.findBySlug(slug) ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
        return modRepository.findDependentsByModSlug(slug)
    }

    fun getRootMods(): List<Mod> {
        return modRepository.findRootMods()
    }

    @Transactional
    fun setUserAdded(id: UUID, userAdded: Boolean) {
        val mod = modRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Mod not found with ID: $id")
        }
        mod.userAdded = userAdded
        modRepository.save(mod)
    }

    fun getDependencyGraph(version: String, loader: Loader): List<ModVersionDependencyGraphDTO> {
        return modVersionRepository.findAllByVersionAndLoaderAndValidationState(
            version = version,
            loader = loader,
            validationState = ValidationState.VALID
        ).map { version ->
            ModVersionDependencyGraphDTO(
                modName = version.mod.title,
                dependencies = version.dependencies.map { it.title }
            )
        }
    }
}
