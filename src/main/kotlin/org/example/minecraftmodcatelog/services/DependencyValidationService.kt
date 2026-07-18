package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import org.example.minecraftmodcatelog.repositories.ModVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DependencyValidationService(
    private val modVersionRepository: ModVersionRepository
) {
    @Transactional
    fun validateAndPersist(
        discoveredVersions: List<ModVersion>,
        version: String,
        loader: Loader
    ): Map<UUID, ValidationState> {
        val cache = mutableMapOf<UUID, ValidationState>()
        val visiting = mutableSetOf<UUID>()
        val discoveredMap = discoveredVersions.associateBy { it.mod.id }

        fun resolveModVersion(depMod: Mod): ModVersion? {
            return discoveredMap[depMod.id] ?: modVersionRepository.findByModAndVersionAndLoader(depMod, version, loader)
        }

        fun validate(mv: ModVersion): ValidationState {
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

            for (depMod in mv.dependencies) {
                val depMv = resolveModVersion(depMod)
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
                modVersionRepository.save(mv)
            }
        }

        return cache
    }
}
