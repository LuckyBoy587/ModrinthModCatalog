package org.example.minecraftmodcatelog.repositories

import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.User
import org.example.minecraftmodcatelog.entities.UserSubscription
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserSubscriptionRepository : JpaRepository<UserSubscription, UUID> {
    fun findByUserAndMod(user: User, mod: Mod): UserSubscription?
    fun existsByUserAndMod(user: User, mod: Mod): Boolean
    fun existsByMod(mod: Mod): Boolean

    @EntityGraph(attributePaths = ["mod"])
    fun findByUser(user: User): List<UserSubscription>
    fun deleteByUserAndMod(user: User, mod: Mod)
    fun deleteByMod(mod: Mod)
}
