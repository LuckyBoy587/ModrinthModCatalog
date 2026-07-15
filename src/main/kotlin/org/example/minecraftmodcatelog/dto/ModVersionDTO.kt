package org.example.minecraftmodcatelog.dto

import org.example.minecraftmodcatelog.entities.ModVersion

interface ModVersionDTO {
    val url: String
    val loader: Loader
    val version: String
    val modName: String
}

data class ModVersionWithDependenciesDTO(
    override val url: String,
    val dependencies: List<String> = listOf(),
    override val loader: Loader,
    override val version: String,
    override val modName: String = "Unknown Mod",
) : ModVersionDTO {
    constructor(modVersion: ModVersion) : this(
        url = modVersion.downloadUrl,
        dependencies = modVersion.dependencies.map { it.modrinthProjectId },
        loader = modVersion.loader,
        version = modVersion.version,
        modName = modVersion.mod.title
    )
}

data class ModVersionWithoutDependenciesDTO(
    override val url: String,
    override val loader: Loader,
    override val version: String,
    override val modName: String = "Unknown Mod",
) : ModVersionDTO {
    constructor(modVersion: ModVersion) : this(
        url = modVersion.downloadUrl,
        loader = modVersion.loader,
        version = modVersion.version,
        modName = modVersion.mod.title
    )
}

enum class Loader(val loaderName: String) {
    FABRIC("fabric"), FORGE("forge");

    override fun toString(): String {
        return loaderName
    }
}
