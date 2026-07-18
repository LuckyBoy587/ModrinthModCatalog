package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.entities.Mod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ModRepository : JpaRepository<Mod, UUID> {
    fun findByModrinthProjectId(modrinthProjectId: String): Mod?
    fun findBySlug(slug: String): Mod?
    fun findAllByUserAdded(userAdded: Boolean): MutableList<Mod>
    fun findAllByModrinthProjectIdIn(modrinthProjectIds: Collection<String>): List<Mod>

    @Query("SELECT DISTINCT dep FROM ModVersion mv JOIN mv.dependencies dep WHERE mv.mod.slug = :slug")
    fun findDependenciesByModSlug(slug: String): List<Mod>

    @Query("SELECT DISTINCT mv.mod FROM ModVersion mv JOIN mv.dependencies dep WHERE dep.slug = :slug")
    fun findDependentsByModSlug(slug: String): List<Mod>
}
