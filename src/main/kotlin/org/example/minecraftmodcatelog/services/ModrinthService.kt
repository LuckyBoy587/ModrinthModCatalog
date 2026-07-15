package org.example.minecraftmodcatelog.services

import com.fasterxml.jackson.annotation.JsonProperty
import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class ModrinthService(
    private val restClient: RestClient
) {
    private fun searchProject(slug: String): ModDTO {
        val url = "https://api.modrinth.com/v2/project/$slug"

        return restClient.get()
            .uri(url)
            .retrieve()
            .body<ModDTO>()
            ?: throw RuntimeException("Failed to fetch modrinthProjectId details from Modrinth for slug or modrinthProjectId ID: $slug")
    }

    fun searchProjectVersion(projectId: String, version: String, loader: Loader): ModVersionWithDependenciesDTO {
        val url =
            "https://api.modrinth.com/v2/project/$projectId/version?game_versions=[\"$version\"]&loaders=[\"$loader\"]&limit=1"

        val response = restClient.get()
            .uri(url)
            .retrieve()
            .body<List<ModrinthVersionResponse>>()

        val versionFileUrl = response?.firstOrNull()?.files?.firstOrNull()?.url
            ?: throw RuntimeException("No version files found for modrinthProjectId: $projectId")

        val dependencies = response.firstOrNull()?.dependencies?.map { it.projectId }
            ?: throw RuntimeException("No dependency found for modrinthProjectId: $projectId")

        return ModVersionWithDependenciesDTO(versionFileUrl, dependencies, loader, version)
    }

    fun searchProjectById(projectId: String): ModDTO {
        return searchProject(projectId)
    }

    fun searchProjectBySlug(slug: String): ModDTO {
        return searchProject(slug)
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