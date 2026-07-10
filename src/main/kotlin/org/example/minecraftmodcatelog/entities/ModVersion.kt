package org.example.minecraftmodcatelog.entities

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "mod_versions")
class ModVersion(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "download_url", columnDefinition = "TEXT")
    var downloadUrl: String,

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    var mod: Mod,

    @OneToMany(mappedBy = "modVersion", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var dependencies: MutableList<ModVersionDependency> = mutableListOf()
) {
}
