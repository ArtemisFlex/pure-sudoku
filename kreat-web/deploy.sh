#!/usr/bin/env bash
# Build Kreat's test arena and put it live at kreat.puregames.app (Vercel project "kreat").
# The quick test build: under a minute after a change.
set -e
cd "$(dirname "$0")"
../gradlew -p . wasmJsBrowserDevelopmentExecutableDistribution
cd build/dist/wasmJs/developmentExecutable
vercel link --yes --project kreat --scope formappteam >/dev/null
vercel deploy --prod --yes --scope formappteam
