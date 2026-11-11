package tech.adw3r.curlconverter

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** What curlconverter returned: either [code] (plus non-fatal [warnings]) or an [error]. */
data class ConversionResult(
    val code: String?,
    val warnings: List<String>,
    val error: String?,
) {
    val isSuccess: Boolean get() = error == null && code != null

    companion object {
        fun fromJson(json: String): ConversionResult = fromJson(JsonParser.parseString(json).asJsonObject)

        fun fromJson(obj: JsonObject): ConversionResult {
            val error = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
            val code = obj.get("code")?.takeIf { !it.isJsonNull }?.asString
            val warnings = obj.getAsJsonArray("warnings")?.map { it.asString } ?: emptyList()
            return ConversionResult(code, warnings, error ?: if (code == null) "curlconverter returned no code" else null)
        }
    }
}

/** The engine itself failed (not available, crashed, timed out) — as opposed to curlconverter rejecting the input. */
class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface ConverterEngine {
    val displayName: String

    /**
     * Blocking; never call on the EDT. Honors cancellation of the current progress indicator.
     * [options] go to the generator as JSON; the curl_cffi target reads `impersonate`.
     */
    @Throws(EngineException::class)
    fun convert(curl: String, target: String, options: Map<String, String> = emptyMap()): ConversionResult
}
