package org.example.minecraftmodcatelog.services

import org.example.minecraftmodcatelog.dto.ModDTO
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class ModrinthService(
    private val restClient: RestClient
) {
    fun searchProject(slug: String): ModDTO {
        val url = "https://api.modrinth.com/v2/project/$slug"

        return restClient.get()
            .uri(url)
            .retrieve()
            .body<ModDTO>()
            ?: throw RuntimeException("Failed to fetch project details from Modrinth for slug: $slug")
    }

    fun searchProjectVersion(projectId: String) {
        
    }
}