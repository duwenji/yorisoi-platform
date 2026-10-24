package io.github.duwenji.yorisoi.kit.channel

import io.github.duwenji.yorisoi.core.format.StationCheck
import io.github.duwenji.yorisoi.core.format.StationFiles
import io.github.duwenji.yorisoi.core.format.StationOrigin
import io.github.duwenji.yorisoi.core.format.StationPackage
import io.github.duwenji.yorisoi.core.format.StationValidator

/**
 * An app's built-in channels, as classpath resources under [resourceDir]`/{id}/` in the same layout
 * as a third-party package. They go through the same checks, so a mistake in the format shows up
 * here first. The standard channel also fills the slots any other channel leaves empty.
 *
 * A jar (or an APK) can't list a resource folder, so the app's build writes every file under
 * [resourceDir], one relative path per line, to [index].
 *
 * @param ids the channels, in the order the app lists them; [standardId] among them
 */
open class ResourceChannels(val resourceDir: String, val standardId: String, val ids: List<String>) {
    val index: String get() = "$resourceDir/index.txt"

    /** The files of the built-in channel [id], read from the resources. */
    fun files(id: String): StationFiles =
        Resources(javaClass.classLoader, "$resourceDir/$id", index().filter { it.startsWith("$id/") }.map { it.removePrefix("$id/") })

    /** Reads and checks the built-in channel in [files]; a rejection means the app's own files are wrong. */
    fun read(id: String, files: StationFiles): StationPackage =
        when (val result = StationValidator.check(files, StationOrigin.BUILT_IN)) {
            is StationCheck.Ok -> result.station.also {
                check(it.manifest.id == id) { "Built-in channel in \"$id\" calls itself \"${it.manifest.id}\"" }
            }
            // Fail loudly rather than run with a broken channel.
            is StationCheck.Rejected -> error("Built-in channel \"$id\" is invalid: ${result.code.json} ${result.detail}")
        }

    /** The standard channel's package: third-party channels fill the slots they leave empty from it. */
    fun standard(files: (String) -> StationFiles = ::files): StationPackage = read(standardId, files(standardId))

    /** Every built-in channel, ready to use; [files] gives the files of one channel by id. */
    fun load(files: (String) -> StationFiles = ::files): List<Station> {
        val packages = ids.associateWith { id -> read(id, files(id)) }
        val standard = packages.getValue(standardId)
        return ids.map { id ->
            val pkg = packages.getValue(id)
            Station(pkg, standard, key = "builtin:${pkg.manifest.id}@${pkg.manifest.version}")
        }
    }

    private fun index(): List<String> {
        val stream = javaClass.classLoader.getResourceAsStream(index) ?: error("No $index: build the prompt pack with Gradle")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { it.isNotBlank() }
    }
}

/** A channel folder among the classpath resources, whose files are known from [paths]. */
private class Resources(private val loader: ClassLoader, private val root: String, private val paths: List<String>) : StationFiles {
    override fun list(): List<String> = paths

    override fun read(path: String): ByteArray? = loader.getResourceAsStream("$root/$path")?.use { it.readBytes() }
}
