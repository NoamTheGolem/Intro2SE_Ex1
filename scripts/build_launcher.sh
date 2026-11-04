#!/usr/bin/env bash
# Builds Ex1.exe (the double-click launcher, launcher/Ex1.c) with the mingw-w64 cross compiler.
# Debian/Ubuntu: apt-get install gcc-mingw-w64-x86-64 binutils-mingw-w64-x86-64
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/launcher
x86_64-w64-mingw32-windres launcher/ex1.rc -O coff -o build/launcher/ex1.res
x86_64-w64-mingw32-gcc -std=c11 -O2 -s -Wall -Wextra -Werror -municode -mwindows \
    -o Ex1.exe launcher/Ex1.c build/launcher/ex1.res -static-libgcc
echo "Built Ex1.exe"
