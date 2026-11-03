package tech.adw3r.curlconverter

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Runs the bundled curlconverter with an external `node` (fallback when JCEF is unavailable). */
internal class NodeEngine(private val configuredPath: () -> String) : ConverterEngine {
    override val displayName: String = "Node.js"

    fun findNode(): String? {
        val configured = configuredPath().trim()
        if (configured.isNotEmpty()) return configured.takeIf { File(it).canExecute() }
        return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("node")?.path
    }

    override fun convert(curl: String, target: String, options: Map<String, String>): ConversionResult {
        val node = findNode() ?: throw EngineException(
            if (configuredPath().isBlank()) "Node.js was not found on PATH" else "Node.js not found at ${configuredPath()}",
        )
        val cmd = GeneralCommandLine(node, script().toString(), target)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            // JSON via the environment: no Windows command-line quoting issues
            .withEnvironment("CURLCONVERTER_OPTIONS", Gson().toJson(options))
        val handler = try {
            CapturingProcessHandler(cmd)
        } catch (e: Exception) {
            throw EngineException("Could not start Node.js ($node): ${e.message}", e)
        }
        handler.processInput.use { it.write(curl.toByteArray(StandardCharsets.UTF_8)) }
        val indicator = ProgressManager.getInstance().progressIndicator
        val out = if (indicator != null) handler.runProcessWithProgressIndicator(indicator, TIMEOUT_MS) else handler.runProcess(TIMEOUT_MS)
        when {
            out.isCancelled -> throw ProcessCanceledException()
            out.isTimeout -> throw EngineException("Node.js did not finish within ${TIMEOUT_MS / 1000} s")
            out.exitCode != 0 || out.stdout.isBlank() ->
                throw EngineException("Node.js exited with code ${out.exitCode}: ${out.stderr.trim().take(500)}")
        }
        return ConversionResult.fromJson(out.stdout)
    }

    /** Extracts the bundle once into the IDE system directory (node needs a real file). */
    private fun script(): Path = synchronized(NodeEngine::class.java) {
        val bytes = Bundle.bytes(Bundle.NODE)
        val dir = Path.of(PathManager.getSystemPath(), "curlconverter")
        val file = dir.resolve("curlconverter-$CURLCONVERTER_VERSION-${bytes.contentHashCode().toUInt().toString(16)}.cjs")
        if (!Files.isRegularFile(file) || Files.size(file) != bytes.size.toLong()) {
            Files.createDirectories(dir)
            val tmp = Files.createTempFile(dir, "curlconverter", ".tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        file
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
