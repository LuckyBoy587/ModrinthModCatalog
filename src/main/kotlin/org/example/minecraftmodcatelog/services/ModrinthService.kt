package org.example.minecraftmodcatelog.services

import com.fasterxml.jackson.annotation.JsonProperty
import org.example.minecraftmodcatelog.dto.ModDTO
import org.example.minecraftmodcatelog.dto.ModVersionDTO
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class ModrinthService(
    private val restClient: RestClient
) {
    fun searchProject(slugOrProjectId: String): ModDTO {
        val url = "https://api.modrinth.com/v2/project/$slugOrProjectId"

        return restClient.get()
            .uri(url)
            .retrieve()
            .body<ModDTO>()
            ?: throw RuntimeException("Failed to fetch project details from Modrinth for slug or project ID: $slugOrProjectId")
    }

    fun searchProjectVersion(projectId: String): ModVersionDTO {
        val gameVersion = "26.2"
        val loader = "fabric"
        val url = "https://api.modrinth.com/v2/project/$projectId/version?game_versions=[\"$gameVersion\"]&loaders=[\"$loader\"]&limit=1"

        val response = restClient.get()
            .uri(url)
            .retrieve()
            .body<List<ModrinthVersionResponse>>()

        val versionFileUrl = response?.firstOrNull()?.files?.firstOrNull()?.url
            ?: throw RuntimeException("No version files found for project: $projectId")

        val dependencies = response.firstOrNull()?.dependencies?.map { it.projectId }
            ?: throw RuntimeException("No dependency found for project: $projectId")

        return ModVersionDTO(versionFileUrl, dependencies)
    }
}

internal data class ModrinthVersionResponse(
    val files: List<ModrinthVersionFile>,
    val dependencies: List<ModrinthDependency>
)

internal data class ModrinthVersionFile(
    val url: String
)

internal data class ModrinthDependency(
    @JsonProperty("project_id") val projectId: String,
)