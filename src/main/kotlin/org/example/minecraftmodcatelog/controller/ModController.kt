package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionWithoutDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.services.ModService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/mod")
class ModController(
    private val modService: ModService
) {
    @PostMapping("/add")
    fun addMod(
        @RequestParam slug: String,
    ): ResponseEntity<String> {
        try {
            modService.loadModBySlug(slug)
            return ResponseEntity.ok("Mod added successfully")
        } catch (e: Exception) {
            return ResponseEntity.status(500).body("Error adding mod: ${e.message}")
        }
    }

    @GetMapping("/version/{slug}")
    fun getModVersion(
        @PathVariable slug: String,
        @RequestParam version: String,
        @RequestParam loader: Loader,
    ): ResponseEntity<Any> {
        return try {
            val modVersion = modService.getOrCreateModVersion(slug, version, loader)
            ResponseEntity.ok(modVersion)
        } catch (e: Exception) {
            ResponseEntity.status(500).body("Error retrieving mod version: ${e.message}")
        }
    }

    @GetMapping("/download")
    fun downloadMods(
        @RequestParam version: String,
        @RequestParam loader: Loader,
    ): ResponseEntity<List<ModVersionWithoutDependenciesDTO>> {
        return try {
            val mods = modService.getModsByVersionAndLoader(version, loader)
            ResponseEntity.ok(mods)
        } catch (_: Exception) {
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @GetMapping("/all")
    fun getAllMods(): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getAllMods())
    }

    @GetMapping("/all/userAdded")
    fun getAllUserAddedMods(): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getAllUserAddedMods())
    }

    @GetMapping("/exists")
    fun existsMod(
        @RequestParam slug: String,
    ): ResponseEntity<Void> {
        if (modService.existsBySlug(slug)) {
            return ResponseEntity.ok().build()
        }
        return ResponseEntity.notFound().build()
    }
}