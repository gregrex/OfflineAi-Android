# Offline AI Assistant (Android Native / Edge AI)

Autonomiczna, lokalna aplikacja na urządzenia Android (zoptymalizowana pod budżetowe smartfony), realizująca inferencję modelu językowego **Qwen2.5-0.5B-Instruct** w 100% offline za pomocą silnika **llama.cpp** przez warstwę C++ / JNI.

## 🚀 Główne Cechy

- **100% Offline (Edge AI):** Brak zewnętrznych zapytań do chmury. Całość obliczeń inferencyjnych wykonuje się na procesorze telefonu (CPU).
- **Format GGUF:** Obsługa 4-bitowej kwantyzacji (`qwen2.5-0.5b-instruct-q4_k_m.gguf`), mieszczącej się w zaledwie ~350-470 MB RAM.
- **Natywne JNI / C++17:** Bezpośrednia integracja z rdzeniem `llama.cpp` w Android NDK (target `arm64-v8a`).
- **Asynchroniczny Streaming (Flow):** Odpowiedzi generowane token po tokenie bez przycinania interfejsu użytkownika.
- **Inżynieria Promptów (System Prompts):** Dedykowane szablony zachowań dla kluczowych kategorii:
  - 🌾 **Rolnictwo:** Tanie, naturalne metody uprawy i nawadniania (Afryka/Ameryka Płd.).
  - 🤝 **Handel UE:** Proste doradztwo eksportowe i certyfikacyjne (Fairtrade) dla spółdzielni.
  - 🛠️ **DIY/Budowa:** Inżynieria przetrwania i tworzenie narzędzi/filtrów z surowców wtórnych.
- **Nowoczesny UI:** Jetpack Compose, Material Design 3, dynamiczne motywy, automatyczny autoscroll.

---

## 🛠️ Stos Technologiczny

- **Język:** Kotlin 2.0 (JVM 21) & C++17
- **UI:** Jetpack Compose + Material 3
- **Architektura:** MVVM, Kotlin Coroutines, StateFlow
- **Inferencja:** `llama.cpp` (kompilowane przez CMakeLists.txt z NDK 27+)
- **Format Promptu:** Qwen2.5 ChatML (`<|im_start|>system...<|im_end|>`)
- **Testy:** JUnit 4 + Coroutines Test

---

## 📥 Pobieranie Modelu (GGUF)

Przed pierwszym zbudowaniem APK należy pobrać plik modelu do folderu `app/src/main/assets/models/`:

```bash
# Na systemach Linux/macOS lub Git Bash (Windows):
./download_model.sh
```

Skrypt pobierze model:
- **URL:** `https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf`
- **Plik docelowy:** `app/src/main/assets/models/llm_model.gguf`

---

## 🏗️ Budowanie i Testy

### Uruchomienie testów jednostkowych:
```bash
./gradlew testDebugUnitTest
```

### Zbudowanie pliku APK:
```bash
./gradlew assembleDebug
```
Wygenerowany plik APK znajdziesz w:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Działanie w Pamięci Urządzenia

1. Przy pierwszym uruchomieniu `ModelExtractor` strumieniowo kopiuje model z `assets` do prywatnego katalogu aplikacji (`context.filesDir/models/llm_model.gguf`).
2. Warstwa C++ JNI (`LlamaCppManager`) mapuje model do pamięci podręcznej i inicjalizuje dedykowany context z oknem 2048 tokenów.
3. Wybór kategorii automatycznie aplikuje kontekstowy `System Prompt` dopasowany do profilu asystenta.
