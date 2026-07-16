package org.example.minecraftmodcatelog.config

import org.example.minecraftmodcatelog.dto.Loader
import org.example.minecraftmodcatelog.exceptions.InvalidRequestException
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class LoaderConverter : Converter<String, Loader> {
    override fun convert(source: String): Loader {
        return try {
            Loader.valueOf(source.uppercase())
        } catch (_: Exception) {
            throw InvalidRequestException(
                "Invalid loader value: $source. Valid values are: ${
                    Loader.entries.joinToString(
                        ", "
                    )
                }"
            )
        }
    }
}

