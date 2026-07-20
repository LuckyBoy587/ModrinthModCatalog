package org.example.minecraftmodcatelog.controller

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.dto.ModVersionDependencyGraphDTO
import org.example.minecraftmodcatelog.dto.ModVersionWithDependenciesDTO
import org.example.minecraftmodcatelog.entities.Mod
import org.example.minecraftmodcatelog.services.ModService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*
import java.util.concurrent.Executors

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

    @GetMapping("/download", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun downloadMods(
        @RequestParam version: String,
        @RequestParam loader: Loader,
    ): SseEmitter {
        val emitter = SseEmitter(180000L) // 3 minutes timeout
        val context = SecurityContextHolder.getContext()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            SecurityContextHolder.setContext(context)
            try {
                val result = modService.getModsByVersionAndLoaderStreaming(version, loader) { resolvedVersion ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name("resolved")
                                .data(resolvedVersion)
                        )
                    } catch (_: Exception) {
                        // Client might have disconnected, but we want to complete processing/caching
                    }
                }
                emitter.send(
                    SseEmitter.event()
                        .name("result")
                        .data(result, MediaType.APPLICATION_JSON)
                )
                emitter.complete()
            } catch (e: Exception) {
                try {
                    emitter.completeWithError(e)
                } catch (_: Exception) {
                    // Ignore
                }
            } finally {
                SecurityContextHolder.clearContext()
                executor.shutdown()
            }
        }
        return emitter
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

    @GetMapping("/dependency-graph")
    fun getDependencyGraph(
        @RequestParam version: String,
        @RequestParam loader: Loader
    ): ResponseEntity<List<ModVersionDependencyGraphDTO>> {
        return ResponseEntity.ok(modService.getDependencyGraph(version, loader))
    }
}
