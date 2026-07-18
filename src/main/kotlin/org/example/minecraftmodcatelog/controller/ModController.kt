package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModResolutionResultDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.services.ModService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/mod")
class ModController(
    private val modService: ModService
) {
    @PostMapping("/add")
    fun addMod(
        @RequestParam slug: String,
    ): ResponseEntity<String> {
        modService.loadModBySlug(slug)
        return ResponseEntity.ok("Mod added successfully")
    }

    @GetMapping("/version/{slug}")
    fun getModVersion(
        @PathVariable slug: String,
        @RequestParam version: String,
        @RequestParam loader: Loader,
    ): ResponseEntity<ModVersionWithDependenciesDTO> {
        val modVersion = modService.getOrCreateModVersion(slug, version, loader)
        return ResponseEntity.ok(modVersion)
    }

    @GetMapping("/download")
    fun downloadMods(
        @RequestParam version: String,
        @RequestParam loader: Loader,
    ): ResponseEntity<ModResolutionResultDTO> {
        val mods = modService.getModsByVersionAndLoader(version, loader)
        return ResponseEntity.ok(mods)
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

    @DeleteMapping("/{id}")
    fun deleteMod(
        @PathVariable id: UUID,
    ): ResponseEntity<String> {
        modService.deleteUserAddedMod(id)
        return ResponseEntity.ok("Mod deleted successfully")
    }

    @DeleteMapping("/delete/{slug}")
    fun deleteModBySlug(
        @PathVariable slug: String,
    ): ResponseEntity<String> {
        modService.deleteModBySlug(slug)
        return ResponseEntity.ok("Mod deleted successfully")
    }

    @GetMapping("/{slug}/dependencies")
    fun getDependencies(
        @PathVariable slug: String,
    ): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getDependencies(slug))
    }

    @GetMapping("/{slug}/dependents")
    fun getDependents(
        @PathVariable slug: String,
    ): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getDependents(slug))
    }

    @GetMapping("/roots")
    fun getRootMods(): ResponseEntity<List<Mod>> {
        return ResponseEntity.ok(modService.getRootMods())
    }

    @PatchMapping("/{id}/userAdded")
    fun setUserAdded(
        @PathVariable id: UUID,
        @RequestParam userAdded: Boolean
    ): ResponseEntity<String> {
        modService.setUserAdded(id, userAdded)
        return ResponseEntity.ok("userAdded value set successfully")
    }
}
