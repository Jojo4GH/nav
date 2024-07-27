
"app/build/bin/linuxX64/debugExecutable/app.kexe" "$@"

navFile="$HOME/.nav-cd"
if [ -f "$navFile" ]; then
    newDir=$(cat "$navFile")
    if [ -n "$newDir" ]; then
        cd "$newDir" || exit
        rm "$navFile"
    fi
fi
