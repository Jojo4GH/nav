package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.which


enum class Shell(
    val shell: String,
    private val pathSanitizer: (String) -> String,
    private val initScript: (binary: String, navFileInHome: String) -> String,
    private val profileLocation: String,
    private val profileDescription: String? = null,
    private val profileCommand: String,
    val execCommandArgs: (String) -> List<String>
) {
    BASH(
        shell = "bash",
        pathSanitizer = UnixPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav () {
                "$binary" --correct-init bash "${'$'}@"
                navFile="${'$'}HOME/$navFileInHome"
                if [ -f "${'$'}navFile" ]; then
                    newDir=${'$'}(cat "${'$'}navFile")
                    if [ -n "${'$'}newDir" ]; then
                        cd "${'$'}newDir" || exit
                        rm -f "${'$'}navFile"
                    fi
                fi
            }
            """.trimIndent()
        },
        profileLocation = "~/.bashrc",
        profileCommand = "eval \"\$(${NavCommand.BinaryName} --init bash)\"",
        execCommandArgs = { listOf("-c", it) }
    ),
    ZSH(
        shell = "zsh",
        pathSanitizer = UnixPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav {
                "$binary" --correct-init zsh "${'$'}@"
                navFile="${'$'}HOME/$navFileInHome"
                if [[ -f "${'$'}navFile" ]]; then
                    newDir=$(cat "${'$'}navFile")
                    if [[ -n "${'$'}newDir" ]]; then
                        cd "${'$'}newDir" || exit
                        rm -f "${'$'}navFile"
                    fi
                fi
            }
            """.trimIndent()
        },
        profileLocation = "~/.zshrc",
        profileCommand = "eval \"\$(${NavCommand.BinaryName} --init zsh)\"",
        execCommandArgs = { listOf("-c", it) }
    ),
    POWERSHELL(
        shell = "powershell",
        pathSanitizer = WindowsPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav {
                & "$binary" --correct-init powershell @args
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
        profileLocation = "\$PROFILE",
        profileCommand = "Invoke-Expression (& ${NavCommand.BinaryName} --init powershell | Out-String)",
        execCommandArgs = { listOf("-NoProfile", "-c", it) }
    ),
    PWSH(
        shell = "pwsh",
        pathSanitizer = WindowsPathSanitizer,
        initScript = { binary, navFileInHome ->
            """
            function nav {
                & "$binary" --correct-init pwsh @args
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
        profileLocation = "\$PROFILE",
        profileCommand = "Invoke-Expression (& ${NavCommand.BinaryName} --init pwsh | Out-String)",
        execCommandArgs = { listOf("-NoProfile", "-c", it) }
    );

    fun printInitScript() {
        val binary = which(NavCommand.BinaryName) ?: error("Could not find ${NavCommand.BinaryName} binary")
        println(initScript(pathSanitizer(binary.toString()), pathSanitizer(CDFile.PathInUserHome.toString())))
    }

    fun printProfileLocation() {
        println(profileLocation)
    }

    fun printProfileCommand() {
        println(profileCommand)
    }

    fun printInitInfo(terminal: Terminal) {
        terminal.println(TextStyles.underline(shell))
        terminal.println(listOfNotNull(
            "Add the following to the end of $profileLocation:",
            profileDescription,
            "",
            "\t$profileCommand",
            "",
        ).joinToString("\n"))
    }

    companion object {
        val available: Map<String, Shell> by lazy { entries.associateBy { it.shell } }

        fun printInitInfo(terminal: Terminal) {
            entries.forEach { it.printInitInfo(terminal) }
        }

        operator fun invoke(shellName: String) = entries
            .firstOrNull { it.shell.equals(shellName, ignoreCase = true) }
            ?: error("Unknown shell: $shellName")
    }
}

typealias InitAction = Shell.(Terminal) -> Unit

private val UnixPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '/')
}

private val WindowsPathSanitizer: (String) -> String = {
    it.replace(RealSystemPathSeparator, '\\')
}
