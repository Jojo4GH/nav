package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.which



fun runInit(shellName: String) {
    val shell = Shells.entries
        .firstOrNull { it.shell.equals(shellName, ignoreCase = true) }
        ?: error("Unknown shell: $shellName")
    shell.printInitScript()
}


private enum class Shells(
    val shell: String,
    private val pathSanitizer: (String) -> String,
    private val initScript: (binary: String, navFileInHome: String) -> String,
    @Suppress("unused") val profileScript: String
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
        profileScript = "eval \"\$(${NavCommand.BinaryName} --init bash)\""
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
        profileScript = "Invoke-Expression (& ${NavCommand.BinaryName} --init powershell | Out-String)"
    );

    fun printInitScript() {
        val binary = which(NavCommand.BinaryName) ?: error("Could not find ${NavCommand.BinaryName} binary")
        println(initScript(pathSanitizer(binary.toString()), pathSanitizer(CDFile.PathInUserHome.toString())))
    }
}

private val UnixPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '/')
}

private val WindowsPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '\\')
}
