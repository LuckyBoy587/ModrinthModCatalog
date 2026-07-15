package org.example.minecraftmodcatelog.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "mods")
class Mod(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "modrinth_project_id", unique = true, columnDefinition = "TEXT")
    var modrinthProjectId: String,

    @Column(name = "slug", columnDefinition = "TEXT")
    var slug: String,

    @Column(name = "title", columnDefinition = "TEXT")
    var title: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String,

    @Column(name = "author", columnDefinition = "TEXT")
    var author: String,

    @Column(name = "icon_url", columnDefinition = "TEXT")
    var iconUrl: String,

    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant,

    @JsonIgnore
    @OneToMany(mappedBy = "mod", cascade = [CascadeType.ALL], orphanRemoval = true)
    var versions: MutableList<ModVersion> = mutableListOf()
)
