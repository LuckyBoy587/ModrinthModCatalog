package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ModWriteService(
    private val modRepository: ModRepository,
    private val modVersionRepository: ModVersionRepository
) {
    @Transactional
    fun saveMod(modDTO: ModDTO, forceUserAdded: Boolean): Mod {
        val existing = modRepository.findByModrinthProjectId(modDTO.modrinthProjectId)
        if (existing != null) {
            if (forceUserAdded && !existing.userAdded) {
                existing.userAdded = true
                return modRepository.save(existing)
            }
            return existing
        }
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

    @Transactional
    fun saveModVersion(
        mod: Mod,
        version: String,
        loader: Loader,
        dto: ModVersionWithDependenciesDTO,
        dependencies: Set<Mod>
    ): ModVersion {
        val existing = modVersionRepository.findByModAndVersionAndLoader(mod, version, loader)
        if (existing != null) {
            return existing
        }
        val modVersion = ModVersion(
            version = version,
            loader = loader,
            mod = mod,
            downloadUrl = dto.url,
            dependencies = dependencies.toMutableSet()
        )
        return modVersionRepository.save(modVersion)
    }
}
