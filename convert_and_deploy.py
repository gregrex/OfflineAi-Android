#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Skrypt łączenia wag LoRA, konwersji do formatu GGUF i automatycznego wdrożenia
nowego modelu na podłączony telefon z Androidem przez ADB.
"""

import os
import sys
import subprocess
import shutil
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel

BASE_MODEL_ID = "Qwen/Qwen2.5-1.5B-Instruct"
LORA_PATH = "./lora_qwen_agriculture"
MERGED_OUTPUT_DIR = "./merged_qwen_agriculture"
GGUF_OUTPUT = "llm_model.gguf"
ADB_PATH = r"C:\Users\gbosk.GREXAI\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def merge_lora():
    print(f"=== Krok 1: Łączenie wag LoRA z modelem bazowym {BASE_MODEL_ID} ===")
    if not os.path.exists(LORA_PATH):
        print(f"Katalog adaptera {LORA_PATH} nie istnieje! Uruchom najpierw train_qlora.py.")
        return False

    print("Ładowanie modelu bazowego w FP16...")
    base_model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL_ID,
        torch_dtype=torch.float16,
        device_map="cpu",
        trust_remote_code=True
    )
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL_ID, trust_remote_code=True)

    print("Ładowanie adaptera LoRA...")
    model = PeftModel.from_pretrained(base_model, LORA_PATH)
    
    print("Scalanie wag (merge_and_unload)...")
    merged_model = model.merge_and_unload()

    print(f"Zapisywanie scalonego modelu do: {MERGED_OUTPUT_DIR}...")
    merged_model.save_pretrained(MERGED_OUTPUT_DIR)
    tokenizer.save_pretrained(MERGED_OUTPUT_DIR)
    print("Scalanie zakończone!")
    return True

def convert_to_gguf():
    print("=== Krok 2: Konwersja do formatu GGUF (llama.cpp) ===")
    converter_script = os.path.join("app", "src", "main", "cpp", "llama.cpp", "convert_hf_to_gguf.py")
    if not os.path.exists(converter_script):
        print(f"Brak skryptu konwertującego: {converter_script}")
        return False

    cmd = [
        sys.executable,
        converter_script,
        MERGED_OUTPUT_DIR,
        "--outfile", GGUF_OUTPUT,
        "--outtype", "q8_0"  # lub f16
    ]
    print("Uruchamianie:", " ".join(cmd))
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print("Błąd konwersji do GGUF!")
        return False
    print(f"Model GGUF wygenerowany pomyślnie: {GGUF_OUTPUT}")
    return True

def deploy_to_device():
    print("=== Krok 3: Wdrażanie modelu na podłączony telefon przez ADB ===")
    if not os.path.exists(GGUF_OUTPUT):
        print(f"Plik {GGUF_OUTPUT} nie został znaleziony!")
        return False

    # Pobierz listę urządzeń
    adb_res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
    lines = [l for l in adb_res.stdout.strip().split("\n")[1:] if l.strip()]
    if not lines:
        print("Nie wykryto żadnego urządzenia Android w ADB!")
        return False

    # Preferuj telefon fizyczny (nie emulator)
    target_device = lines[0].split()[0]
    for line in lines:
        if "emulator" not in line and "device" in line:
            target_device = line.split()[0]
            break

    print(f"Docelowe urządzenie: {target_device}")

    # Skopiuj do pamięci aplikacji na telefonie
    target_app_dir = "/data/user/0/com.offlineai.assistant/files/models/llm_model.gguf"
    sdcard_target = "/sdcard/Download/llm_model.gguf"

    print(f"Przesyłanie nowego modelu do {sdcard_target}...")
    subprocess.run([ADB_PATH, "-s", target_device, "push", GGUF_OUTPUT, sdcard_target])

    # Kopiuj do katalogu assets w projekcie
    assets_target = os.path.join("app", "src", "main", "assets", "models", "llm_model.gguf")
    shutil.copy2(GGUF_OUTPUT, assets_target)
    print(f"Zaktualizowano również zasób lokalny: {assets_target}")

    # Zresetuj aplikację na telefonie
    print("Restartowanie aplikacji na telefonie...")
    subprocess.run([ADB_PATH, "-s", target_device, "shell", "am", "force-stop", "com.offlineai.assistant"])
    subprocess.run([ADB_PATH, "-s", target_device, "shell", "run-as", "com.offlineai.assistant", "cp", sdcard_target, target_app_dir])
    subprocess.run([ADB_PATH, "-s", target_device, "shell", "am", "start", "-n", "com.offlineai.assistant/.MainActivity"])

    print("=== Nowy model został pomyślnie wdrożony i uruchomiony na telefonie! ===")
    return True

def main():
    if merge_lora():
        if convert_to_gguf():
            deploy_to_device()

if __name__ == "__main__":
    main()
