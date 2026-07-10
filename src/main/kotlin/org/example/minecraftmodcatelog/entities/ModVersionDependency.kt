package org.example.minecraftmodcatelog.entities

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "mod_version_dependency")
class ModVersionDependency(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "project_id", columnDefinition = "TEXT", nullable = false)
    var project: String,

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_version_id", nullable = false)
    var modVersion: ModVersion
)