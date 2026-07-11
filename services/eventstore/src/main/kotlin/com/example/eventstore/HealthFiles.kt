package com.example.eventstore

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

object HealthFiles {
    private val readyPath = Path.of("/tmp/eventstore.ready")
    private val alivePath = Path.of("/tmp/eventstore.alive")

    fun markReady() {
        touch(readyPath)
        markAlive()
    }

    fun markAlive() {
        touch(alivePath)
    }

    private fun touch(path: Path) {
        Files.writeString(
            path,
            Instant.now().toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
