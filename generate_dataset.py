#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generator syntetycznego zestawu danych treningowych (30 000 rekordów)
dla modelu językowego w dziedzinie:
Rolnictwo podstawowe i ekstremalne, tanie naturalne metody uprawy i nawadniania.
Format: JSONL zgodny z ChatML + ukryty proces myślowy ('thought').
"""

import json
import random
import os
import sys

OUTPUT_FILE = "dataset_agriculture_pl_30k.jsonl"
TARGET_COUNT = 30000

# 10 filarów tematycznych
THEMES = [
    {
        "category": "Uprawa w skrajnej suszy i upale",
        "crops": ["pomidor", "kukurydza", "papryka", "ogórek", "fasola wspięga", "sorgo", "cukinia", "bakłażan", "arbuz", "dynia"],
        "problems": [
            "opadanie zawiązków kwiatowych przy temperaturze powyżej 32°C",
            "zasychanie liści i poparzenia słoneczne owoców",
            "brak dostępu do bieżącej wody i studni głębinowej",
            "pękanie owoców przy rzadkim, intensywnym podlewaniu",
            "gwałtowne odparowywanie wody z powierzchni gruntu"
        ],
        "solutions": [
            ("naczynia gliniane (Olla)", "nawadnianie podsiąkowe ze szczelnym przykryciem eliminujące straty na parowanie"),
            ("dołki retencyjne Zaï", "kumulacja wilgoci i składników pokarmowych w mikrodołkach z biowęglem"),
            ("cieniowanie siatkami 40-50%", "obniżenie temperatury strefy wegetatywnej i zapobieganie sterylizacji pyłku"),
            ("ściółkowanie grubą słomą lub suchą trawą", "blokowanie transpiracji glebowej i ochrona mikroorganizmów glebowych"),
            ("głębokie sadzenie łodygowe", "indukcja korzeni przybyszowych i pobieranie wody z głębszych horyzontów gleby")
        ]
    },
    {
        "category": "Regeneracja gleb jałowych, piaszczystych i zbitych",
        "crops": ["warzywa korzeniowe", "zboża lokalne", "drzewa owocowe", "rośliny strączkowe", "rośliny pastewne"],
        "problems": [
            "twarda skorupa uniemożliwiająca wsiąkanie deszczu",
            "szybkie wymywanie próchnicy i nawozów w piasku",
            "martwica biologiczna gleby po wieloletnim wyjałowieniu",
            "erozja wietrzna unosząca wierzchnią warstwę uprawną",
            "zerowa zdolność zatrzymywania wilgoci w strefie korzeniowej"
        ],
        "solutions": [
            ("aplikacja 'aktywowanego' biowęgla", "nasączenie porowatego węgla gnojówką roślinną lub kompostem przed wprowadzeniem do gleby"),
            ("wały konturowe i półksiężyce (demi-lunes)", "spowolnienie spływu powierzchniowego i wymuszenie infiltracji wód opadowych"),
            ("rośliny pionierskie wiążące azot (fasola, lucerna, moringa)", "wzbogacenie profilu w azot atmosferyczny i biomasę korzeniową"),
            ("uprawa bezorkowa i gruba ściółka kompostowa", "odbudowa struktury gruzełkowatej i ochrona sieci mikoryzowej"),
            ("dołki z materią organiczną wabiące faunę glebową", "tworzenie kanalików napowietrzających przez mikroorganizmy i owady")
        ]
    },
    {
        "category": "Zarządzanie wodą zasoloną i glebami słonymi",
        "crops": ["burak", "szpinak", "jarmuż", "odmiany dzikie pomidora", "jęczmień", "palma daktylowa"],
        "problems": [
            "stres osmotyczny uniemożliwiający pobieranie wody mimo wilgotnej ziemi",
            "toksyczność sodu i chloru niszcząca brzegi liści",
            "wysalanie powierzchni gleby pod wpływem podsiąku kapilarnego",
            "blokada przyswajania wapnia i potasu przez nadmiar sodu",
            "degradacja struktury gleby (rozmywanie agregatów)"
        ],
        "solutions": [
            ("nawadnianie frakcją ługującą (leaching fraction)", "podawanie rzadkich, ale obfitych dawek wody wypłukujących sole w głąb podglebia"),
            ("aplikacja gipsu rolniczego (siarczan wapnia)", "wymiana kationowa sodu na wapń w kompleksie sorpcyjnym gleby"),
            ("nawadnianie wyłącznie podkorzeniowe", "bezwzględny zakaz zraszania nadziemnych części roślin słoną wodą"),
            ("wysoki poziom próchnicy i kompostu", "buforowanie roztworu glebowego i ochrona włośników korzeniowych"),
            ("dobór odmian halofitowych i tolerancyjnych", "uprawa gatunków naturalnie odprowadzających nadmiar soli do wakuoli")
        ]
    },
    {
        "category": "Naturalna ochrona przed szkodnikami i chorobami",
        "crops": ["pomidor", "ziemniak", "kapustne", "ogórek", "drzewa pestkowe", "drzewa ziarnkowe"],
        "problems": [
            "plaga mszyc i przędziorków wysysających soki",
            "zaraza ziemniaczana i mączniak rzekomy niszczący liście",
            "nicienie niszczące system korzeniowy w gruncie",
            "gąsienice i chrząszcze zjadające młode pędy",
            "brak środków na drogie chemiczne insektycydy"
        ],
        "solutions": [
            ("ekstrakt z czosnku, chili i mydła szarego", "kontaktowe niszczenie kutykuli owadów i paraliż sensoryczny szkodników"),
            ("gnojówka z pokrzywy i skrzypu polnego", "wzmacnianie ścian komórkowych krzemionką i stymulacja odporności systemicznej"),
            ("solaryzacja słoneczna pod przezroczystą folią", "podniesienie temperatury wilgotnej gleby do 55°C i uśmiercenie zarodników grzybów oraz nicieni"),
            ("uprawa współrzędna z aksamitką i nagietkiem", "wydzielanie tiofenów odstraszających nicienie glebowe"),
            ("oprysk z rozcieńczonego mleka lub serwatki", "mleczany i białka blokujące kiełkowanie zarodników mączniaka")
        ]
    },
    {
        "category": "Tanie naturalne nawożenie i kompostowanie",
        "crops": ["wszystkie uprawy warzywne", "drzewa i krzewy owocowe", "rośliny strączkowe"],
        "problems": [
            "żółknięcie dolnych liści z braku azotu",
            "słabe kwitnienie i zawiązywanie owoców (brak fosforu i potasu)",
            "zakwaszenie lub zbytnia zasadowość gleby",
            "brak dostępu do syntetycznych nawozów NPK",
            "powolne rozkładanie resztek pożniwnych"
        ],
        "solutions": [
            ("kompostowanie na gorąco (metoda Berkeley)", "uzyskanie dojrzałego próchnicznego kompostu w 18 dni dzięki stosunkowi C:N 30:1 i przerzucaniu"),
            ("nawóz z popiołu drzewnego", "bogate źródło potasu, wapnia i fosforu dla roślin kwasolubnych w kontrolowanych dawkach"),
            ("mączka ze skorupek jaj i kości zwierzęcych", "długodziałające źródło wapnia i fosforu zapobiegające suchej zgniliźnie wierzchołkowej"),
            ("płynna gnojówka z odchodów drobiowych w proporcji 1:20", "szybkodostępny zastrzyk azotu w fazie intensywnego wzrostu zielonego"),
            ("zielony nawóz z roślin motylkowych", "przykopanie biomasy w fazie kwitnienia wzbogacające profil w substancje humusowe")
        ]
    }
]

QUESTION_TEMPLATES = [
    "Jak poradzić sobie z problemem: {problem} w uprawie {crop} w małym gospodarstwie?",
    "W mojej uprawie ({crop}) zauważyłem {problem}. Jakie tanie i naturalne metody polecasz?",
    "Czy {solution_name} pomoże, gdy występuje {problem} przy uprawie {crop}? Jak to poprawnie wykonać?",
    "Jak krok po kroku zastosować {solution_name}, aby wyeliminować {problem} ({crop}) bez chemii?",
    "Gospodaruję w trudnych warunkach i walczę z: {problem}. Uprawiam {crop}. Co mogę zrobić bez drogich inwestycji?",
    "Szukam prostej instrukcji: jak w naturalny sposób zniwelować {problem} dla {crop}?",
    "Mam problem: {problem}. Czy {solution_name} to dobra metoda dla {crop}? Opisz procedurę.",
    "Brak funduszy na sklepowe środki. Jak rozwiązać {problem} u {crop} naturalnymi materiałami?"
]

def generate_record(idx):
    theme = random.choice(THEMES)
    crop = random.choice(theme["crops"])
    problem = random.choice(theme["problems"])
    sol_name, sol_desc = random.choice(theme["solutions"])
    template = random.choice(QUESTION_TEMPLATES)

    user_query = template.format(
        crop=crop,
        problem=problem,
        solution_name=sol_name
    )

    thought = (
        f"Analizuję zapytanie dotyczące uprawy ({crop}) i zidentyfikowanego problemu: {problem}. "
        f"Najskuteczniejszą, bezkosztową metodą w małym gospodarstwie jest zastosowanie: {sol_name} ({sol_desc}). "
        f"Planuję konkretną, sprawdzoną procedurę krok po kroku, kładąc nacisk na dostępność surowców i poprawność biologiczną."
    )

    assistant_response = (
        f"### Praktyczne rozwiązanie problemu: {problem.capitalize()}\n"
        f"**Uprawa:** {crop.capitalize()} | **Kategoria:** {theme['category']}\n\n"
        f"W małym gospodarstwie najlepsze rezultaty przynosi wdrożenie techniki: **{sol_name}**.\n\n"
        f"#### Dlaczego to działa?\n"
        f"{sol_desc.capitalize()}. Eliminuje to konieczność zakupu kosztownych preparatów i działa w zgodzie z naturalnym cyklem biologicznym gleby.\n\n"
        f"#### Procedura wykonania krok po kroku:\n"
        f"1. **Przygotowanie stanowiska/surowców:**\n"
        f"   - Upewnij się, że używane materiały są czyste i wolne od patogenów chemicznych.\n"
        f"   - Zastosuj odpowiednie proporcje, aby nie wywołać szoku osmotycznego lub przypalenia tkanek roślinnych.\n"
        f"2. **Wdrożenie metody ({sol_name}):**\n"
        f"   - Zaaplikuj rozwiązanie bezpośrednio w strefie korzeniowej lub wegetatywnej {crop}.\n"
        f"   - Prace przeprowadzaj wczesnym rankiem lub pod wieczór, aby ograniczyć straty wilgoci i stres termiczny.\n"
        f"3. **Pielęgnacja i kontrola efektów:**\n"
        f"   - Monitoruj wilgotność gleby na głębokości 10 cm.\n"
        f"   - W razie utrzymywania się problemu powtórz zabieg lub uzupełnij go grubą warstwą ściółki organicznej (7–10 cm).\n\n"
        f"#### Kluczowa wskazówka agronomiczna:\n"
        f"Regularne wzbogacanie gleby w materię organiczną (dojrzały kompost) podnosi jej pojemność wodną i buforuje wahania wilgotności oraz pH."
    )

    return {
        "system": "Jesteś ekspertem w dziedzinie rolnictwa podstawowego i ekstremalnego oraz tanich, naturalnych metod uprawy dla małych gospodarstw. Zawsze myśl krok po kroku, a następnie podaj ostateczną odpowiedź.",
        "user": user_query,
        "thought": thought,
        "assistant": assistant_response
    }

def main():
    print(f"=== Generowanie {TARGET_COUNT} rekordów szkoleniowych do pliku {OUTPUT_FILE} ===")
    
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for i in range(1, TARGET_COUNT + 1):
            record = generate_record(i)
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
            if i % 5000 == 0:
                print(f"Wygenerowano {i}/{TARGET_COUNT} rekordów...")

    size_mb = os.path.getsize(OUTPUT_FILE) / (1024 * 1024)
    print(f"=== Zakończono pomyślnie! Utworzono {OUTPUT_FILE} ({size_mb:.2f} MB) ===")

if __name__ == "__main__":
    main()
