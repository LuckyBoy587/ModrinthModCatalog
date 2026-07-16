package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.exceptions.ResourceNotFoundException
import org.example.minecraftmodcatelog.exceptions.InvalidStateException
import org.example.minecraftmodcatelog.exceptions.ExternalServiceException
import org.example.minecraftmodcatelog.exceptions.GlobalExceptionHandler
import org.example.minecraftmodcatelog.services.ModService
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class ModControllerTest {

    private lateinit var mockMvc: MockMvc
    private val modService: ModService = mock(ModService::class.java)

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ModController(modService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `deleteMod throws ResourceNotFoundException returns 404 with custom payload`() {
        val uuid = UUID.randomUUID()
        `when`(modService.deleteUserAddedMod(uuid)).thenThrow(
            ResourceNotFoundException("Mod not found with ID: $uuid")
        )

        mockMvc.perform(delete("/mod/$uuid"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status", `is`(404)))
            .andExpect(jsonPath("$.error", `is`("Not Found")))
            .andExpect(jsonPath("$.message", `is`("Mod not found with ID: $uuid")))
            .andExpect(jsonPath("$.path", `is`("/mod/$uuid")))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `deleteMod throws InvalidStateException returns 409 with custom payload`() {
        val uuid = UUID.randomUUID()
        `when`(modService.deleteUserAddedMod(uuid)).thenThrow(
            InvalidStateException("Only user-added mods can be deleted")
        )

        mockMvc.perform(delete("/mod/$uuid"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status", `is`(409)))
            .andExpect(jsonPath("$.error", `is`("Conflict")))
            .andExpect(jsonPath("$.message", `is`("Only user-added mods can be deleted")))
            .andExpect(jsonPath("$.path", `is`("/mod/$uuid")))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `addMod throws ExternalServiceException returns 502 with custom payload`() {
        val slug = "jei"
        `when`(modService.loadModBySlug(slug)).thenThrow(
            ExternalServiceException("Modrinth API returned an error (HTTP 500) when retrieving mod 'jei'.")
        )

        mockMvc.perform(post("/mod/add").param("slug", slug))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status", `is`(502)))
            .andExpect(jsonPath("$.error", `is`("Bad Gateway")))
            .andExpect(jsonPath("$.message", `is`("Modrinth API returned an error (HTTP 500) when retrieving mod 'jei'.")))
            .andExpect(jsonPath("$.path", `is`("/mod/add")))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `deleteModBySlug is accessible at mod level delete`() {
        val slug = "iris"
        
        mockMvc.perform(delete("/mod/delete/$slug"))
            .andExpect(status().isOk)
    }
}
