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

import org.example.minecraftmodcatelog.entities.ValidationState
import java.util.UUID

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
            val urlChanged = existing.downloadUrl != dto.url
            val depsChanged = existing.dependencies != dependencies
            if (urlChanged || depsChanged) {
                existing.downloadUrl = dto.url
                existing.dependencies = dependencies.toMutableSet()
                existing.validationState = ValidationState.UNKNOWN
                invalidateDependents(existing.mod)
                return modVersionRepository.save(existing)
            }
            return existing
        }
        val modVersion = ModVersion(
            version = version,
            loader = loader,
            mod = mod,
            downloadUrl = dto.url,
            dependencies = dependencies.toMutableSet(),
            validationState = ValidationState.UNKNOWN
        )
        invalidateDependents(mod)
        return modVersionRepository.save(modVersion)
    }

    private fun invalidateDependents(mod: Mod, visited: MutableSet<UUID> = mutableSetOf()) {
        val dependents = modVersionRepository.findByDependencyMod(mod)
        for (depMv in dependents) {
            if (depMv.id !in visited) {
                visited.add(depMv.id)
                if (depMv.validationState != ValidationState.UNKNOWN) {
                    depMv.validationState = ValidationState.UNKNOWN
                    modVersionRepository.save(depMv)
                }
                invalidateDependents(depMv.mod, visited)
            }
        }
    }
}
