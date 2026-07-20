package org.example.minecraftmodcatelog.config

import org.example.minecraftmodcatelog.entities.User
import org.example.minecraftmodcatelog.entities.UserRole
import org.example.minecraftmodcatelog.repositories.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseSeeder {

    @Bean
    fun seedDatabase(userRepository: UserRepository): CommandLineRunner {
        return CommandLineRunner {
            val adminEmail = "admin@example.com"
            if (!userRepository.existsByEmail(adminEmail)) {
                val adminUser = User(
                    email = adminEmail,
                    password = "\$2a\$12\$ILoHc7PKn6WF5rIsZEnX3e2JskQ9tDuqVUgIRjKR4JN.XOG05K9M.",
                    role = UserRole.ROLE_ADMIN
                )
                userRepository.save(adminUser)
                println("Admin user seeded successfully with email: $adminEmail")
            }
        }
    }
}
