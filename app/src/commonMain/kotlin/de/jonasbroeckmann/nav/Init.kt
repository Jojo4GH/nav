package de.jonasbroeckmann.nav


enum class Shells(
    val shell: String,
    private val pathSanitizer: (String) -> String,
    private val initScript: (binary: String, navFileInHome: String) -> String,
    val profileScript: String
) {
    BASH(
        shell = "bash",
        pathSanitizer = UnixPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav () {
                "$binary" "${'$'}@"
                navFile="${'$'}HOME/$navFileInHome"
                if [ -f "${'$'}navFile" ]; then
                    newDir=${'$'}(cat "${'$'}navFile")
                    if [ -n "${'$'}newDir" ]; then
                        cd "${'$'}newDir" || exit
                        rm "${'$'}navFile"
                    fi
                fi
            }
            """.trimIndent()
        },
        profileScript = "eval \"\$($NAV --init bash)\""
    ),
    POWERSHELL(
        shell = "powershell",
        pathSanitizer = WindowsPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav {
                & "$binary" @args
                ${'$'}navFile = "${'$'}HOME\$navFileInHome"
                if (Test-Path ${'$'}navFile) {
                    ${'$'}newDir = Get-Content ${'$'}navFile
                    if (${'$'}newDir) {
                        Set-Location ${'$'}newDir
                        Remove-Item ${'$'}navFile
                    }
                }
            }
            """.trimIndent()
        },
        profileScript = "Invoke-Expression (& $NAV --init powershell | Out-String)"
    );

    fun printInitScript() {
        val binary = which(NAV) ?: error("Could not find $NAV binary")
        println(initScript(pathSanitizer(binary.toString()), pathSanitizer(NavFileInUserHome.toString())))
    }
}


private const val NAV = "nav"

private val UnixPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '/')
}

private val WindowsPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '\\')
}
