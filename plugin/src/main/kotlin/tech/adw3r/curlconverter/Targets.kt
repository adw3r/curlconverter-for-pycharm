package tech.adw3r.curlconverter

/** Version of the bundled curlconverter (must match the bundles in resources/curlconverter). */
const val CURLCONVERTER_VERSION = "4.12.0"

/** A curlconverter output language. [id] is the same as the curlconverter CLI `--language` value. */
data class Target(val id: String, val label: String) {
    override fun toString(): String = label
}

object Targets {
    const val DEFAULT_ID = "python-curl-cffi"

    /** Same ids as js/src/entry.js; a test checks the bundle knows every one. */
    val ALL: List<Target> = listOf(
        Target("python-curl-cffi", "Python (curl_cffi)"),
        Target("python", "Python (requests)"),
        Target("python-http", "Python (http.client)"),
        Target("ansible", "Ansible"),
        Target("c", "C"),
        Target("cfml", "ColdFusion"),
        Target("clojure", "Clojure"),
        Target("csharp", "C#"),
        Target("dart", "Dart"),
        Target("elixir", "Elixir"),
        Target("go", "Go"),
        Target("har", "HAR"),
        Target("http", "HTTP"),
        Target("httpie", "HTTPie"),
        Target("java", "Java (HttpClient)"),
        Target("java-httpurlconnection", "Java (HttpURLConnection)"),
        Target("java-jsoup", "Java (jsoup)"),
        Target("java-okhttp", "Java (OkHttp)"),
        Target("javascript", "JavaScript (fetch)"),
        Target("javascript-jquery", "JavaScript (jQuery)"),
        Target("javascript-xhr", "JavaScript (XHR)"),
        Target("json", "JSON"),
        Target("julia", "Julia"),
        Target("kotlin", "Kotlin"),
        Target("lua", "Lua"),
        Target("matlab", "MATLAB"),
        Target("node", "Node.js (fetch)"),
        Target("node-axios", "Node.js (Axios)"),
        Target("node-got", "Node.js (got)"),
        Target("node-http", "Node.js (http)"),
        Target("node-ky", "Node.js (Ky)"),
        Target("node-request", "Node.js (request)"),
        Target("node-superagent", "Node.js (SuperAgent)"),
        Target("objc", "Objective-C"),
        Target("ocaml", "OCaml"),
        Target("perl", "Perl"),
        Target("php", "PHP (curl)"),
        Target("php-guzzle", "PHP (Guzzle)"),
        Target("php-requests", "PHP (Requests)"),
        Target("powershell", "PowerShell (Invoke-RestMethod)"),
        Target("powershell-webrequest", "PowerShell (Invoke-WebRequest)"),
        Target("r", "R (httr)"),
        Target("r-httr2", "R (httr2)"),
        Target("ruby", "Ruby (Net::HTTP)"),
        Target("ruby-httparty", "Ruby (HTTParty)"),
        Target("rust", "Rust"),
        Target("swift", "Swift"),
        Target("wget", "Wget"),
    )

    fun byId(id: String?): Target = ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == DEFAULT_ID }

    /** File extension for scratch files holding code in this language, or null if there is no obvious one. */
    fun extension(id: String): String? = when {
        id.startsWith("python") -> "py"
        id.startsWith("javascript") || id.startsWith("node") -> "js"
        id.startsWith("java") -> "java"
        id.startsWith("php") -> "php"
        id.startsWith("ruby") -> "rb"
        id.startsWith("powershell") -> "ps1"
        id == "r" || id == "r-httr2" -> "R"
        else -> when (id) {
            "ansible" -> "yml"; "c" -> "c"; "cfml" -> "cfm"; "clojure" -> "clj"; "csharp" -> "cs"
            "dart" -> "dart"; "elixir" -> "ex"; "go" -> "go"; "har", "json" -> "json"; "http" -> "http"
            "httpie", "wget" -> "sh"; "julia" -> "jl"; "kotlin" -> "kt"; "lua" -> "lua"; "matlab" -> "m"
            "objc" -> "m"; "ocaml" -> "ml"; "perl" -> "pl"; "rust" -> "rs"; "swift" -> "swift"
            else -> null
        }
    }
}
