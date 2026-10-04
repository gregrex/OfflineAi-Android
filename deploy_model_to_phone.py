#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Skrypt bezpośredniego wdrożenia nowego modelu GGUF na telefon POCO F2 Pro oraz do aplikacji.
"""

import os
import sys
import subprocess
import shutil

ADB_PATH = r"C:\Users\gbosk.GREXAI\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def deploy(model_source_path):
    if not os.path.exists(model_source_path):
        print(f"Błąd: Plik {model_source_path} nie istnieje!")
        return False

    print(f"=== Wdrażanie modelu: {model_source_path} ===")
    
    # 1. Kopiowanie do zasobów projektu
    target_asset = os.path.join("app", "src", "main", "assets", "models", "llm_model.gguf")
    print(f"Kopiowanie do zasobów aplikacji: {target_asset}...")
    shutil.copy2(model_source_path, target_asset)

    # 2. Wykrycie telefonu
    res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
    devices = [line.split()[0] for line in res.stdout.strip().split("\n")[1:] if "device" in line]
    
    if not devices:
        print("Nie wykryto żadnego urządzenia w ADB!")
        return False

    phone_id = next((d for d in devices if "emulator" not in d), devices[0])
    print(f"Wybrane urządzenie: {phone_id}")

    # 3. Wysłanie pliku na telefon
    remote_tmp = "/sdcard/Download/llm_model.gguf"
    print(f"Przesyłanie na telefon ({phone_id}) do {remote_tmp}...")
    subprocess.run([ADB_PATH, "-s", phone_id, "push", model_source_path, remote_tmp])

    # 4. Zastąpienie pliku w katalogu prywatnym aplikacji (jeśli debugowalna)
    print("Aktualizacja pliku modelu w aplikacji...")
    subprocess.run([ADB_PATH, "-s", phone_id, "shell", "am", "force-stop", "com.offlineai.assistant"])
    subprocess.run([ADB_PATH, "-s", phone_id, "shell", "run-as", "com.offlineai.assistant", "cp", remote_tmp, "/data/user/0/com.offlineai.assistant/files/models/llm_model.gguf"])
    
    # 5. Uruchomienie aplikacji
    print("Uruchamianie zaktualizowanej aplikacji...")
    subprocess.run([ADB_PATH, "-s", phone_id, "shell", "am", "start", "-n", "com.offlineai.assistant/.MainActivity"])
    print("=== Model został zaktualizowany i aplikacja działa! ===")
    return True

if __name__ == "__main__":
    if len(sys.argv) > 1:
        deploy(sys.argv[1])
    else:
        print("Użycie: python deploy_model_to_phone.py <sciezka_do_modelu.gguf>")
