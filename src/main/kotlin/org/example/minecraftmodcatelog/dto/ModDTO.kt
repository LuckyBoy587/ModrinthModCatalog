package org.example.minecraftmodcatelog.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class ModDTO(
    @JsonProperty("id") val modrinthProjectId: String,
    val slug: String,
    val title: String,
    val description: String,
    @JsonProperty("team") val author: String,
    @JsonProperty("icon_url") val iconUrl: String?,
    val lastSyncedAt: Instant = Instant.now()
)