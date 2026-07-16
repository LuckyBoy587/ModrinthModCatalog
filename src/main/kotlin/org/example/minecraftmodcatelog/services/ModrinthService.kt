package org.example.minecraftmodcatelog.services

import com.fasterxml.jackson.annotation.JsonProperty
import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.exceptions.ExternalServiceException
import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body

@Service
class ModrinthService(
    private val restClient: RestClient
) {
    private fun searchProject(slug: String): ModDTO {
        val url = "https://api.modrinth.com/v2/project/$slug"

        try {
            return restClient.get()
                .uri(url)
                .retrieve()
                .body<ModDTO>()
                ?: throw ExternalServiceException("Received empty response from Modrinth for mod '$slug'")
        } catch (e: HttpClientErrorException.NotFound) {
            throw ResourceNotFoundException("Mod with slug or ID '$slug' was not found on Modrinth.", e)
        } catch (e: RestClientResponseException) {
            throw ExternalServiceException(
                "Modrinth API returned an error (HTTP ${e.statusCode}) when retrieving mod '$slug'.",
                e
            )
        } catch (e: Exception) {
            throw ExternalServiceException("Failed to connect or retrieve mod '$slug' from Modrinth API.", e)
        }
    }

    fun searchProjectVersion(mod: Mod, version: String, loader: Loader): ModVersionWithDependenciesDTO {
        val url =
            "https://api.modrinth.com/v2/project/${mod.modrinthProjectId}/version?game_versions=[\"$version\"]&loaders=[\"$loader\"]&limit=1"

        val response = try {
            restClient.get()
                .uri(url)
                .retrieve()
                .body<List<ModrinthVersionResponse>>()
        } catch (e: HttpClientErrorException.NotFound) {
            throw ResourceNotFoundException("Mod with title '${mod.title}' was not found on Modrinth.", e)
        } catch (e: RestClientResponseException) {
            throw ExternalServiceException(
                "Modrinth API returned an error (HTTP ${e.statusCode}) when retrieving version '$version' for mod '${mod.title}'.",
                e
            )
        } catch (e: Exception) {
            throw ExternalServiceException("Failed to connect or retrieve version details from Modrinth.", e)
        }

        val matchingVersion = response?.firstOrNull()
            ?: throw ResourceNotFoundException("No matching version found on Modrinth for mod '${mod.title}', version '$version', and loader '$loader'.")

        val versionFileUrl = matchingVersion.files.firstOrNull()?.url
            ?: throw ResourceNotFoundException("No download files found on Modrinth for mod '${mod.title}', version '$version', and loader '$loader'.")

        val dependencies = matchingVersion.dependencies.map { it.projectId }

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
