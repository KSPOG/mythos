#!/usr/bin/env bash
set -eu
cd -- "$(dirname -- "$0")"
marker=.server-restart-request
forge_args=libraries/net/minecraftforge/forge/1.21.1-52.1.5/unix_args.txt
[[ -f "$forge_args" && -f user_jvm_args.txt ]] || { echo 'Missing Forge 52.1.5 argument files' >&2; exit 1; }
java_bin=java
if [[ -n "${JAVA_HOME:-}" ]]; then java_bin="$JAVA_HOME/bin/java"; fi
while true; do
    rm -f -- "$marker"
    export KSP_RESTART_TOKEN="$(cat /proc/sys/kernel/random/uuid)"
    rc=0
    "$java_bin" @user_jvm_args.txt "@$forge_args" nogui || rc=$?
    requested=""
    if [[ -f "$marker" ]]; then requested="$(cat "$marker")"; rm -f -- "$marker"; fi
    if [[ "$rc" != 0 || "$requested" != "$KSP_RESTART_TOKEN" ]]; then exit "$rc"; fi
    echo 'Clean restart requested. Starting Forge again in 5 seconds.'
    sleep 5
done
