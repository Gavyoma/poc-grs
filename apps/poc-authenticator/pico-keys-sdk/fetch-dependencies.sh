#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

cd "$SCRIPT_DIR"

CONFIG_FILE=".gitmodules"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: $CONFIG_FILE not found in $SCRIPT_DIR."
  exit 1
fi

echo "Reading dependencies from $CONFIG_FILE..."

git config --file "$CONFIG_FILE" --get-regexp 'url$' | while read -r key url; do
    base_key="${key%.url}"

    target_path=$(git config --file "$CONFIG_FILE" --get "${base_key}.path")

    target_hash=$(git config --file "$CONFIG_FILE" --get "${base_key}.hash" || echo "")

    echo "----------------------------------------"

    if [ -d "$target_path" ] && [ "$(ls -A "$target_path")" ]; then
        echo "Directory '$target_path' already exists and is not empty. Skipping clone..."
    else
        echo "Cloning $url into $target_path..."
        git clone "$url" "$target_path"

        if [ -n "$target_hash" ]; then
            echo "Checking out specific hash: $target_hash"
            (cd "$target_path" && git checkout "$target_hash")
        fi

        echo "Fetching nested submodules..."
        (cd "$target_path" && git submodule update --init --recursive)
    fi
done

echo "----------------------------------------"
echo "Done fetching dependencies!"