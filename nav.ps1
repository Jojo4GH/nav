
& "$PSScriptRoot\app\build\bin\mingwX64\debugExecutable\app.exe"

$navFile = "$HOME\.nav-cd"
if (Test-Path $navFile) {
    $newDir = Get-Content $navFile
    if ($newDir) {
        Set-Location $newDir
        Remove-Item $navFile
    }
}
