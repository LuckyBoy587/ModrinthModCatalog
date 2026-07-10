package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ModVersionDependency
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionDependencyRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ModService(
    private val modrinthService: ModrinthService,
    private val modRepository: ModRepository,
    private val modVersionRepository: ModVersionRepository,
    private val modVersionDependencyRepository: ModVersionDependencyRepository
) {
    @Transactional
    fun saveModBySlug(slug: String) {
        val existingBySlug = modRepository.findBySlug(slug)
        if (existingBySlug != null) {
            return
        }

        val modDTO = modrinthService.searchProject(slug)

        val mod = Mod(
            modrinthProjectId = modDTO.modrinthProjectId,
            slug = modDTO.slug,
            title = modDTO.title,
            description = modDTO.description,
            author = modDTO.author,
            iconUrl = modDTO.iconUrl ?: "",
            lastSyncedAt = modDTO.lastSyncedAt
        )

        val modVersionUrlDTO = modrinthService.searchProjectVersion(modDTO.modrinthProjectId)

        val modVersion = ModVersion(
            mod = mod,
            downloadUrl = modVersionUrlDTO.url,
        )

        val dependencies = modVersionUrlDTO.dependencies.map { depProjectId ->
            ModVersionDependency(
                project = depProjectId,
                modVersion = modVersion
            )
        }
        modVersion.dependencies.addAll(dependencies)

        modRepository.save(mod)
        modVersionRepository.save(modVersion)
        modVersionDependencyRepository.saveAll(dependencies)
    }

    fun getAllMods(): List<Mod> {
        return modRepository.findAll()
    }
}
