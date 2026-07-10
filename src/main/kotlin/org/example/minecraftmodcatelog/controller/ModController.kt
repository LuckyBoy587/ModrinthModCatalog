package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.services.ModService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ModController(
    private val modService: ModService
) {
    @PostMapping("/add-mod")
    fun addMod(@RequestParam slug: String): ResponseEntity<String> {
        try {
            modService.saveModBySlug(slug)
            return ResponseEntity.ok("Mod added successfully")
        } catch (_: Exception) {
            return ResponseEntity.status(500).body("Error adding mod")
        }
    }

    @GetMapping("/all-mods")
    fun getAllMods(): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getAllMods())
    }
}