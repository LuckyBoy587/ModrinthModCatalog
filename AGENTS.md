# Agent Guidelines: MinecraftModCatalog

Welcome, Agent! This document outlines the project context, technical stack, architecture, guidelines, and commands for developing the **MinecraftModCatalog** application.

---

## 🚀 Project Overview

**MinecraftModCatalog** is a catalog application for managing and displaying Minecraft mods. It is a Kotlin-based backend service built on the Spring Boot framework.

### Tech Stack & Versions
- **Language**: Kotlin 2.1+ / 2.3+ (configured with standard plugins: `spring`, `jpa`, `all-open`)
- **JDK Version**: Java 25
- **Framework**: Spring Boot 4.1.0
- **Database**: MySQL (Database name: `mod_catalog_db`)
- **Build Tool**: Maven

---

## 🛠️ Commands Reference

Use the following commands for building, running the application:

| Action | Command |
| :--- | :--- |
| **Build Project** | `.\mvnw.cmd clean package` (Windows) or `./mvnw clean package` (POSIX) |
| **Run Locally** | `.\mvnw.cmd spring-boot:run` |

---

## 🏗️ Architecture & Package Structure

The project follows a standard Spring Boot layered architecture:

- **Entity / Model**: Database models mapped via JPA (e.g., `Mod`, `Author`, `Category`).
- **Repository**: Spring Data JPA repositories (interfaces extending `JpaRepository` or `CrudRepository`).
- **Service**: Business logic layer (annotated with `@Service`).
- **Controller**: Web controllers (annotated with `@RestController` or `@Controller`).

### Base Package
`org.example.minecraftmodcatelog`
- All source files reside under `src/main/kotlin/org/example/minecraftmodcatelog/`.

---

## ✍️ Coding Guidelines

### 1. Kotlin & Java 25 Style
- Use idiomatic Kotlin: prefer expression bodies, null-safety checks (`?.`, `?:`), and data classes for DTOs.
- Avoid using Java-style boilerplate in Kotlin where possible.
- Utilize modern Java 25 features if interacting with Java APIs or utilizing virtual threads (`spring.threads.virtual.enabled=true`).
- Keep classes and methods open when required by Spring using the Kotlin `all-open` plugin (configured for `@Entity`, `@MappedSuperclass`, `@Embeddable`).

### 2. Spring Data JPA
- Make sure entity classes have a default constructor (handled automatically by the `kotlin-maven-noarg` / `jpa` compiler plugins).
- Use `lateinit var` or nullable types carefully. Prefer constructor injection for dependencies in Spring components.

### 3. Database Configurations
- Default local database is MySQL running on `localhost:3306` with credentials `root` / `root`.
- Schema changes should be tracked carefully.

---

## 🤖 Agent Guardrails & Best Practices

1. **Safety First**: Do not modify database connection details in `application.properties` unless explicitly requested.
2. **Kotlin Conventions**: Ensure all file names match their primary class name or are descriptive utility names (e.g., `Filename.kt`).
3. **Preserve Documentation**: Do not remove existing docstrings or comments.
