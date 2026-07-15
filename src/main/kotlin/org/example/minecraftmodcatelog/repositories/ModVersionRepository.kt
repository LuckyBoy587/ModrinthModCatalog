package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ModVersionRepository : JpaRepository<ModVersion, UUID> {
    fun findByModAndVersionAndLoader(mod: Mod, version: String, loader: Loader): ModVersion?
    fun findAllByVersionAndLoader(
        version: String,
        loader: Loader
    ): MutableList<ModVersion>
}
