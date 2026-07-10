package org.example.minecraftmodcatelog.dto

data class ModVersionDTO(
    val url: String,
    val dependencies: List<String> = listOf(),
)
