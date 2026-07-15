package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ModVersionDependency
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
    fun loadModBySlug(slug: String): Mod {
        val existingBySlug = modRepository.findBySlug(slug)
        if (existingBySlug != null) {
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
            lastSyncedAt = modDTO.lastSyncedAt
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
            lastSyncedAt = modDTO.lastSyncedAt
        )
        return modRepository.save(mod)
    }

    private fun createModVersion(mod: Mod, version: String, loader: Loader): ModVersion {
        val existingVersion = modVersionRepository.findByModAndVersionAndLoader(mod, version, loader)
        if (existingVersion != null) {
            return existingVersion
        }
        val modVersionDTO = modrinthService.searchProjectVersion(mod.modrinthProjectId, version, loader)
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
        val mod = loadModBySlug(slug)
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
        val mods = modRepository.findAll()
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
}

