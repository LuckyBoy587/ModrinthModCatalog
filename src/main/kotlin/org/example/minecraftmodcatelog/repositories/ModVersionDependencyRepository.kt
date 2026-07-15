package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.entities.ModVersionDependency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ModVersionDependencyRepository : JpaRepository<ModVersionDependency, UUID> {
    fun findAllByModrinthProjectId(modrinthProjectId: String): List<ModVersionDependency>
}
