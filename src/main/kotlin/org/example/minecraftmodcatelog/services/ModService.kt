package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ModService(
    private val modrinthService: ModrinthService,
    private val modRepository: ModRepository
) {
    @Transactional
    fun saveModBySlug(slug: String): Mod {
        val modDTO = modrinthService.searchProject(slug)
        val existingMod = modRepository.findByModrinthProjectId(modDTO.modrinthProjectId)

        val mod = existingMod?.apply {
            this.slug = modDTO.slug
            this.title = modDTO.title
            this.description = modDTO.description
            this.author = modDTO.author
            this.iconUrl = modDTO.iconUrl ?: ""
            this.lastSyncedAt = modDTO.lastSyncedAt
        }
            ?: Mod(
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
}
