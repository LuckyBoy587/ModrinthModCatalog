package org.example.minecraftmodcatelog.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.example.minecraftmodcatelog.dto.Loader
import java.util.*

@Entity
@Table(name = "mod_versions")
class ModVersion(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "version")
    var version: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "loader")
    var loader: Loader,

    @Column(name = "download_url", columnDefinition = "TEXT")
    var downloadUrl: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    var mod: Mod,

    @JsonIgnore
    @OneToMany(mappedBy = "modVersion", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var dependencies: MutableList<ModVersionDependency> = mutableListOf()
)
