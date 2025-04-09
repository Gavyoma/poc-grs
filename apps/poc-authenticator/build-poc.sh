#!/bin/bash

PICO_SDK_PATH=../../../../pico-sdk
VERSION_MAJOR="5"
VERSION_MINOR="12"

rm -rf release/*
mkdir -p build_release
mkdir -p release
cd build_release || exit

boards=("pico")

for board_name in "${boards[@]}"
do
    rm -rf ./**
    PICO_SDK_PATH="${PICO_SDK_PATH:-../../../pico-sdk}" cmake .. -DPICO_BOARD="$board_name"
    # The -k option tells make to continue running even if some commands fail. By default, make will stop immediately when a command fails.
    # The -j option specifies the number of jobs that can be run in parallel. By default, make runs one job at a time. When you specify -j20, you're telling make to run up to 20 jobs simultaneously.
    make -j20
    cp pico_fido.uf2 ../release/fido-"$board_name"-$VERSION_MAJOR-$VERSION_MINOR-"$(date +%d%m%Y_%H%M%S)".uf2
done
