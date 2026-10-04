#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Skrypt Fine-Tuningu (QLoRA) dla modelu klasy 2B (np. Qwen2.5-1.5B-Instruct)
na zbiorze danych rolnictwa podstawowego i ekstremalnego (dataset_agriculture_pl_30k.jsonl).
"""

import os
import sys
import torch
from datasets import load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments
)
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from trl import SFTTrainer

MODEL_ID = "Qwen/Qwen2.5-1.5B-Instruct"
DATASET_PATH = "dataset_agriculture_pl_30k.jsonl"
OUTPUT_DIR = "./lora_qwen_agriculture"

def format_chatml(example):
    """Formatuje wpis do standardu ChatML z ukrytym procesem myślowym."""
    text = (
        f"<|im_start|>system\n{example['system']}<|im_end|>\n"
        f"<|im_start|>user\n{example['user']}<|im_end|>\n"
        f"<|im_start|>thought\n{example['thought']}<|im_end|>\n"
        f"<|im_start|>assistant\n{example['assistant']}<|im_end|>\n"
    )
    return {"text": text}

def main():
    print(f"=== Rozpoczynanie przygotowania treningu QLoRA dla: {MODEL_ID} ===")
    
    if not torch.cuda.is_available():
        print("UWAGA: Brak dostępu do GPU z obsługą CUDA! Trening QLoRA wymaga karty NVIDIA.")
        sys.exit(1)

    print(f"Używane GPU: {torch.cuda.get_device_name(0)}")
    
    # 1. Konfiguracja kwantyzacji 4-bit (QLoRA)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True
    )

    # 2. Ładowanie tokenizera i modelu bazowego
    print("Ładowanie tokenizera...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, trust_remote_code=True)
    tokenizer.pad_token = tokenizer.eos_token

    print("Ładowanie modelu w 4-bit...")
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True
    )
    model = prepare_model_for_kbit_training(model)

    # 3. Konfiguracja adaptera LoRA
    peft_config = LoraConfig(
        r=16,
        lora_alpha=32,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )
    model = get_peft_model(model, peft_config)
    model.print_trainable_parameters()

    # 4. Ładowanie i formatowanie datasetu
    print(f"Ładowanie datasetu z {DATASET_PATH}...")
    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")
    formatted_dataset = dataset.map(format_chatml, remove_columns=dataset.column_names)

    # 5. Parametry treningu
    training_args = TrainingArguments(
        output_dir=OUTPUT_DIR,
        per_device_train_batch_size=4,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        logging_steps=10,
        num_train_epochs=1,
        max_steps=500,  # Dostosuj według potrzeb (np. 1000 lub 1 epoch)
        fp16=True,
        optim="paged_adamw_8bit",
        save_strategy="steps",
        save_steps=100,
        save_total_limit=2,
        report_to="none"
    )

    # 6. Trainer
    trainer = SFTTrainer(
        model=model,
        train_dataset=formatted_dataset,
        dataset_text_field="text",
        max_seq_length=1024,
        tokenizer=tokenizer,
        args=training_args
    )

    print("=== Rozpoczynanie procesu treningowego ===")
    trainer.train()

    print(f"Zapisywanie adaptera LoRA do: {OUTPUT_DIR}")
    model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)
    print("=== Trening zakończony sukcesem! ===")

if __name__ == "__main__":
    main()
