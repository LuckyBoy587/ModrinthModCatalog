package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModResolutionResultDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
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
                    org.example.minecraftmodcatelog.dto.ModDTO(
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

    fun loadModsAndDependencies(
        version: String,
        loader: Loader
    ): Pair<List<ModVersionWithoutDependenciesDTO>, List<String>> {
        val discoveredVersions = discoverModsAndDependencies(version, loader)
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

    private fun discoverModsAndDependencies(version: String, loader: Loader): List<ModVersion> {
        val rootMods = modRepository.findAllByUserAdded(true)
        val queue: Queue<ModVersion> = LinkedList()
        val loadedProjectIds = mutableSetOf<String>()
        val discoveredVersions = mutableListOf<ModVersion>()

        for (mod in rootMods) {
            try {
                val mv = createModVersion(mod, version, loader)
                queue.add(mv)
                loadedProjectIds.add(mod.modrinthProjectId)
                discoveredVersions.add(mv)
            } catch (e: ResourceNotFoundException) {
                // Ignore missing versions during discovery
            }
        }

        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            for (depMod in curr.dependencies) {
                if (depMod.modrinthProjectId !in loadedProjectIds) {
                    loadedProjectIds.add(depMod.modrinthProjectId)
                    try {
                        val depMv = createModVersion(depMod, version, loader)
                        queue.add(depMv)
                        discoveredVersions.add(depMv)
                    } catch (e: ResourceNotFoundException) {
                        // Ignore missing dependency versions during discovery
                    }
                }
            }
        }
        return discoveredVersions
    }

    fun getModsByVersionAndLoader(version: String, loader: Loader): ModResolutionResultDTO {
        val (workingDtos, missingDependencies) = loadModsAndDependencies(version, loader)
        return ModResolutionResultDTO(
            available = workingDtos,
            unavailable = missingDependencies
        )
    }


    fun getAllUserAddedMods(): List<Mod> {
        return modRepository.findAllByUserAdded(true)
    }

    @Transactional
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
}
