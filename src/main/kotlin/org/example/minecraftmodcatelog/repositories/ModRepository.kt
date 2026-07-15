package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.entities.Mod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ModRepository : JpaRepository<Mod, UUID> {
    fun findByModrinthProjectId(modrinthProjectId: String): Mod?
    fun findBySlug(slug: String): Mod?
}
