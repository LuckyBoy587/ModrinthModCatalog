package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.dto.ModDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.services.ModService
import org.example.minecraftmodcatelog.services.ModrinthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ModController(
    private val modrinthService: ModrinthService,
    private val modService: ModService
) {
    @GetMapping("/get-mod-details")
    fun getModDetails(@RequestParam slug: String): ModDTO {
        val mod = modrinthService.searchProject(slug)
        return mod
    }

    @PostMapping("/add-mod")
    fun addMod(@RequestParam slug: String): Mod {
        return modService.saveModBySlug(slug)
    }
}