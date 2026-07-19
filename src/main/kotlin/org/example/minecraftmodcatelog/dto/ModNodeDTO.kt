package org.example.minecraftmodcatelog.dto

import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.entities.ModVersion
import org.example.minecraftmodcatelog.entities.ValidationState
import java.util.UUID

data class ModNodeDTO(
    val id: UUID,
    val modrinthProjectId: String,
    val title: String,
    val slug: String
) {
    constructor(mod: Mod) : this(
        id = mod.id,
        modrinthProjectId = mod.modrinthProjectId,
        title = mod.title,
        slug = mod.slug
    )
}

data class ModVersionNodeDTO(
    val id: UUID,
    val version: String,
    val loader: Loader,
    val downloadUrl: String,
    val mod: ModNodeDTO,
    val dependencyProjectIds: Set<String>,
    var validationState: ValidationState
) {
    constructor(mv: ModVersion) : this(
        id = mv.id,
        version = mv.version,
        loader = mv.loader,
        downloadUrl = mv.downloadUrl,
        mod = ModNodeDTO(mv.mod),
        dependencyProjectIds = mv.dependencies.map { it.modrinthProjectId }.toSet(),
        validationState = mv.validationState
    )
}
