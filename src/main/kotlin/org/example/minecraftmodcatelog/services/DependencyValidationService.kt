package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionNodeDTO
import org.example.minecraftmodcatelog.entities.ValidationState
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.example.minecraftmodcatelog.repositories.ModRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DependencyValidationService(
    private val modVersionRepository: ModVersionRepository,
    private val modRepository: ModRepository
) {
    @Transactional
    fun validateAndPersist(
        discoveredVersions: List<ModVersionNodeDTO>,
        version: String,
        loader: Loader
    ): Map<UUID, ValidationState> {
        val cache = mutableMapOf<UUID, ValidationState>()
        val visiting = mutableSetOf<UUID>()
        val discoveredMap = discoveredVersions.associateBy { it.mod.id }

        fun resolveModVersion(depModId: UUID, depModrinthId: String): ModVersionNodeDTO? {
            val local = discoveredMap[depModId]
            if (local != null) return local
            val depMod = modRepository.findById(depModId).orElse(null) ?: return null
            val mvEntity = modVersionRepository.findByModAndVersionAndLoader(depMod, version, loader) ?: return null
            return ModVersionNodeDTO(mvEntity)
        }

        fun validate(mv: ModVersionNodeDTO): ValidationState {
            val cached = cache[mv.id]
            if (cached != null) {
                return cached
            }

            if (mv.id in visiting) {
                // Cycle detected
                return ValidationState.INVALID
            }

            // If we already have a persisted validation state that is not UNKNOWN, reuse it
            if (mv.validationState != ValidationState.UNKNOWN) {
                cache[mv.id] = mv.validationState
                return mv.validationState
            }

            visiting.add(mv.id)

            if (mv.downloadUrl.isBlank()) {
                visiting.remove(mv.id)
                cache[mv.id] = ValidationState.INVALID
                return ValidationState.INVALID
            }

            // In our DTO, mv.dependencyProjectIds contains Modrinth Project IDs.
            // Let's resolve the actual database mods by these project IDs.
            val depMods = modRepository.findAllByModrinthProjectIdIn(mv.dependencyProjectIds)
            if (depMods.size < mv.dependencyProjectIds.size) {
                // Some dependency mods aren't even imported in the DB yet
                visiting.remove(mv.id)
                cache[mv.id] = ValidationState.INVALID
                return ValidationState.INVALID
            }

            for (depMod in depMods) {
                val depMv = resolveModVersion(depMod.id, depMod.modrinthProjectId)
                if (depMv == null) {
                    visiting.remove(mv.id)
                    cache[mv.id] = ValidationState.INVALID
                    return ValidationState.INVALID
                }

                if (validate(depMv) == ValidationState.INVALID) {
                    visiting.remove(mv.id)
                    cache[mv.id] = ValidationState.INVALID
                    return ValidationState.INVALID
                }
            }

            visiting.remove(mv.id)
            cache[mv.id] = ValidationState.VALID
            return ValidationState.VALID
        }

        // Validate all discovered versions
        for (mv in discoveredVersions) {
            validate(mv)
        }

        // Persist the validation state back to the database
        for (mv in discoveredVersions) {
            val state = cache[mv.id] ?: ValidationState.UNKNOWN
            if (mv.validationState != state) {
                mv.validationState = state
                val dbEntity = modVersionRepository.findById(mv.id).orElse(null)
                if (dbEntity != null) {
                    dbEntity.validationState = state
                    modVersionRepository.save(dbEntity)
                }
            }
        }

        return cache
    }
}

