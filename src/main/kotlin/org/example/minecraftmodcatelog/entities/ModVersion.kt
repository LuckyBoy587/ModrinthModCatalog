package org.example.minecraftmodcatelog.entities

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "mod_versions")
class ModVersion(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    var mod: Mod,

    @Column(name = "download_url", columnDefinition = "TEXT")
    var downloadUrl: String
)
