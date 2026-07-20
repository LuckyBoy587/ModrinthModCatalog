package org.example.minecraftmodcatelog.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "user_subscriptions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "mod_id"])]
)
class UserSubscription(
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    var mod: Mod,

    @Column(name = "subscribed_at", nullable = false)
    var subscribedAt: Instant = Instant.now()
)
