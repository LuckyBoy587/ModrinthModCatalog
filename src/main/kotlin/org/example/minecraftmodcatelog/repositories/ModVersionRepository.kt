package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface ModVersionRepository : JpaRepository<ModVersion, UUID> {
    @EntityGraph(attributePaths = ["dependencies", "mod"])
    fun findByModAndVersionAndLoader(mod: Mod, version: String, loader: Loader): ModVersion?

    @Query("SELECT COUNT(mv) FROM ModVersion mv JOIN mv.dependencies d WHERE d = :depMod AND mv.mod NOT IN :modsToDelete")
    fun countOtherDependents(depMod: Mod, modsToDelete: Collection<Mod>): Long

    @Query("SELECT mv FROM ModVersion mv JOIN mv.dependencies d WHERE d = :mod")
    fun findByDependencyMod(mod: Mod): List<ModVersion>

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM mod_version_dependency WHERE mod_version_id IN :versionIds", nativeQuery = true)
    fun deleteDependenciesByVersionIds(versionIds: Collection<UUID>)

    fun findAllByVersionAndLoaderAndValidationState(
        version: String,
        loader: Loader,
        validationState: ValidationState
    ): MutableList<ModVersion>
}
