package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.entities.ModVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ModVersionRepository : JpaRepository<ModVersion, UUID> {
}
