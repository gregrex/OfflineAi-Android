#!/usr/bin/env bash
set -e

MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
OUTPUT_DIR="app/src/main/assets/models"
OUTPUT_FILE="$OUTPUT_DIR/llm_model.gguf"

mkdir -p "$OUTPUT_DIR"

echo "=== Pobieranie uaktualnionego modelu Qwen2.5-1.5B-Instruct GGUF ==="
echo "URL: $MODEL_URL"
echo "Docelowy plik: $OUTPUT_FILE"

if command -v curl >/dev/null 2>&1; then
    curl -L --retry 3 -C - -o "$OUTPUT_FILE" "$MODEL_URL"
elif command -v wget >/dev/null 2>&1; then
    wget -c -O "$OUTPUT_FILE" "$MODEL_URL"
else
    echo "Brak curl i wget w systemie!"
    exit 1
fi

echo "=== Pobieranie zakonczone pomyslnie! ==="
ls -lh "$OUTPUT_FILE"
