package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ModVersionDependency
import org.example.minecraftmodcatelog.exceptions.InvalidStateException
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionDependencyRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ModService(
    private val modrinthService: ModrinthService,
    private val modRepository: ModRepository,
    private val modVersionRepository: ModVersionRepository,
    private val modVersionDependencyRepository: ModVersionDependencyRepository
) {
    fun loadModBySlug(slug: String, forceUserAdded: Boolean = true): Mod {
        val existingBySlug = modRepository.findBySlug(slug)
        if (existingBySlug != null) {
            if (forceUserAdded && !existingBySlug.userAdded) {
                existingBySlug.userAdded = true
                return modRepository.save(existingBySlug)
            }
            return existingBySlug
        }

        val modDTO = modrinthService.searchProjectBySlug(slug)
        val mod = Mod(
            modrinthProjectId = modDTO.modrinthProjectId,
            slug = modDTO.slug,
            title = modDTO.title,
            description = modDTO.description,
            author = modDTO.author,
            iconUrl = modDTO.iconUrl ?: "",
            lastSyncedAt = modDTO.lastSyncedAt,
            userAdded = forceUserAdded
        )
        return modRepository.save(mod)
    }

    fun loadModByProjectId(projectId: String): Mod {
        val existingByProjectId = modRepository.findByModrinthProjectId(projectId)
        if (existingByProjectId != null) {
            return existingByProjectId
        }

        val modDTO = modrinthService.searchProjectById(projectId)
        val mod = Mod(
            modrinthProjectId = modDTO.modrinthProjectId,
            slug = modDTO.slug,
            title = modDTO.title,
            description = modDTO.description,
            author = modDTO.author,
            iconUrl = modDTO.iconUrl ?: "",
            lastSyncedAt = modDTO.lastSyncedAt,
            userAdded = false
        )
        return modRepository.save(mod)
    }

    private fun createModVersion(mod: Mod, version: String, loader: Loader): ModVersion {
        val existingVersion = modVersionRepository.findByModAndVersionAndLoader(mod, version, loader)
        if (existingVersion != null) {
            return existingVersion
        }
        val modVersionDTO = modrinthService.searchProjectVersion(mod, version, loader)
        val modVersion = ModVersion(
            version = version,
            loader = loader,
            mod = mod,
            downloadUrl = modVersionDTO.url,
        )

        val dependencies = modVersionDTO.dependencies.map { depProjId ->
            ModVersionDependency(
                modrinthProjectId = depProjId,
                modVersion = modVersion
            )
        }
        modVersion.dependencies.addAll(dependencies)

        modVersionRepository.save(modVersion)
        modVersionDependencyRepository.saveAll(dependencies)

        return modVersion
    }

    @Transactional
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

    fun loadModsAndDependencies(version: String, loader: Loader) {
        val mods = modRepository.findAllByUserAdded(true)
        val modVersionQueue: Queue<ModVersion> = LinkedList()
        val loadedProjectIds: MutableSet<String> = mutableSetOf()
        for (mod in mods) {
            modVersionQueue.add(createModVersion(mod, version, loader))
            loadedProjectIds.add(mod.modrinthProjectId)
        }

        while (modVersionQueue.isNotEmpty()) {
            val currModVersion = modVersionQueue.poll()
            for (dependency in currModVersion.dependencies) {
                if (dependency.modrinthProjectId !in loadedProjectIds) {
                    loadedProjectIds.add(dependency.modrinthProjectId)
                    val depMod = loadModByProjectId(dependency.modrinthProjectId)
                    val depModVersion = createModVersion(depMod, version, loader)
                    modVersionQueue.add(depModVersion)
                }
            }
        }
    }

    @Transactional
    fun getModsByVersionAndLoader(version: String, loader: Loader): List<ModVersionWithoutDependenciesDTO> {
        loadModsAndDependencies(version, loader)
        return modVersionRepository.findAllByVersionAndLoader(version, loader).map { modVersion ->
            ModVersionWithoutDependenciesDTO(modVersion)
        }
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

            // Find all project IDs that currentMod depends on
            val dependencyProjectIds = currentMod.versions.flatMap { v ->
                v.dependencies.map { d -> d.modrinthProjectId }
            }.toSet()

            for (depProjectId in dependencyProjectIds) {
                val depMod = modRepository.findByModrinthProjectId(depProjectId) ?: continue
                if (modsToDelete.contains(depMod)) {
                    continue
                }

                val allDependencies = modVersionDependencyRepository.findAllByModrinthProjectId(depProjectId)
                val otherDependents = allDependencies.filter { dep ->
                    val dependentMod = dep.modVersion.mod
                    !modsToDelete.contains(dependentMod)
                }

                if (otherDependents.isEmpty()) {
                    modsToDelete.add(depMod)
                    queue.add(depMod)
                }
            }
        }

        val versionsToDelete = modsToDelete.flatMap { it.versions }.toList()
        val dependenciesToDelete = versionsToDelete.flatMap { it.dependencies }.toList()

        for (version in versionsToDelete) {
            version.dependencies.clear()
        }
        for (mod in modsToDelete) {
            mod.versions.clear()
        }

        modVersionDependencyRepository.deleteAllInBatch(dependenciesToDelete)
        modVersionRepository.deleteAllInBatch(versionsToDelete)
        modRepository.deleteAllInBatch(modsToDelete)
    }

    fun deleteModBySlug(slug: String) {
        val mod = modRepository.findBySlug(slug) ?: throw ResourceNotFoundException("Mod not found with slug: $slug")
        deleteUserAddedMod(mod.id)
    }
}

