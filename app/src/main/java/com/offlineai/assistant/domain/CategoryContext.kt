package com.offlineai.assistant.domain

/**
 * Model reprezentujący kontekst tematyczny i powiązany z nim System Prompt dla Edge AI.
 */
enum class AssistantCategory(
    val categoryId: String,
    val title: String,
    val description: String,
    val systemPrompt: String
) {
    AGRICULTURE(
        categoryId = "agriculture",
        title = "Rolnictwo",
        description = "Tanie, naturalne metody uprawy i nawadniania dla małych gospodarstw",
        systemPrompt = "Jesteś ekspertem ds. rolnictwa w Afryce i Ameryce Płd. Używasz prostego języka. Zawsze doradzasz tanie, naturalne metody uprawy i nawadniania. Odpowiadaj zwięźle."
    ),
    EU_TRADE(
        categoryId = "eu_trade",
        title = "Handel UE",
        description = "Zasady eksportu i certyfikacji Fairtrade dla spółdzielni",
        systemPrompt = "Jesteś doradcą biznesowym dla małych spółdzielni. Tłumaczysz zasady eksportu i certyfikacji (np. Fairtrade) w bardzo prostych krokach."
    ),
    SURVIVAL_DIY(
        categoryId = "survival_diy",
        title = "DIY/Budowa",
        description = "Tworzenie narzędzi i filtrów z darmowych surowców i odpadów",
        systemPrompt = "Jesteś inżynierem survivalowym. Krok po kroku tłumaczysz, jak zbudować narzędzia (np. filtry do wody) z darmowych śmieci i lokalnych surowców."
    );

    companion object {
        fun fromId(id: String): AssistantCategory {
            return entries.find { it.categoryId == id } ?: AGRICULTURE
        }
    }
}

object CategoryContext {

    /**
     * Zwraca predefiniowaną listę kategorii asystenta.
     */
    fun getAllCategories(): List<AssistantCategory> = AssistantCategory.entries

    /**
     * Formatuje prompt w standardzie Qwen2.5 ChatML:
     * <|im_start|>system
     * [SYSTEM_PROMPT]<|im_end|>
     * <|im_start|>user
     * [USER_MESSAGE]<|im_end|>
     * <|im_start|>assistant
     */
    fun formatPrompt(category: AssistantCategory, userMessage: String): String {
        return buildString {
            append("<|im_start|>system\n")
            append(category.systemPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userMessage.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /**
     * Formatuje prompt z historią konwersacji zachowując system prompt kategorii.
     */
    fun formatConversation(
        category: AssistantCategory,
        history: List<Pair<String, String>>, // role to content ("user" or "assistant")
        newUserMessage: String
    ): String {
        return buildString {
            append("<|im_start|>system\n")
            append(category.systemPrompt)
            append("<|im_end|>\n")
            for ((role, content) in history) {
                append("<|im_start|>")
                append(role)
                append("\n")
                append(content.trim())
                append("<|im_end|>\n")
            }
            append("<|im_start|>user\n")
            append(newUserMessage.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }
}
