#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <unistd.h>
#include <algorithm>

#include "logging.h"
#include "llama.h"
#include "common.h"
#include "sampling.h"

static llama_model       * g_model    = nullptr;
static llama_context     * g_context  = nullptr;
static common_batch        g_batch;
static common_sampler    * g_sampler  = nullptr;

static int g_current_position = 0;
static int g_max_predict_position = 0;
static std::string g_cached_token_chars;

static bool is_valid_utf8(const char *string) {
    if (!string) return true;
    const auto *bytes = (const unsigned char *) string;
    int num = 0;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeInit(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jnative_lib_dir) {
    if (jnative_lib_dir != nullptr) {
        const char *path = env->GetStringUTFChars(jnative_lib_dir, nullptr);
        LOGi("Loading backends from %s", path);
        ggml_backend_load_all_from_path(path);
        env->ReleaseStringUTFChars(jnative_lib_dir, path);
    }
    llama_backend_init();
    LOGi("Llama backend initialized");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jmodel_path,
        jint n_ctx) {
    if (g_model != nullptr) {
        LOGw("Model already loaded. Unloading previous model.");
        if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
        if (g_context) { llama_free(g_context); g_context = nullptr; }
        llama_model_free(g_model);
        g_model = nullptr;
    }

    const char *model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("Loading model from: %s", model_path);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!g_model) {
        LOGe("Failed to load model from file");
        return JNI_FALSE;
    }

    // Configure context for Edge AI devices
    int actual_n_ctx = n_ctx > 0 ? n_ctx : 2048;
    int n_threads = std::max(2, std::min(4, (int) sysconf(_SC_NPROCESSORS_ONLN) - 1));
    LOGi("Using %d threads, context size: %d", n_threads, actual_n_ctx);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = actual_n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) {
        LOGe("Failed to create llama context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_batch = common_batch(g_context);

    // Initialize sampler with friendly, coherent chat defaults
    common_params_sampling sparams;
    sparams.temp = 0.6f;
    sparams.top_k = 40;
    sparams.top_p = 0.9f;
    sparams.penalty_repeat = 1.1f;
    g_sampler = common_sampler_init(g_model, sparams);

    LOGi("Model and context initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeStartCompletion(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jprompt,
        jint max_tokens) {
    if (!g_model || !g_context) {
        LOGe("Cannot start completion: Model or context is null");
        return JNI_FALSE;
    }

    const char *prompt_str = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_str);
    env->ReleaseStringUTFChars(jprompt, prompt_str);

    // Clear previous KV cache memory
    llama_memory_clear(llama_get_memory(g_context), false);
    g_cached_token_chars.clear();
    g_current_position = 0;

    // Tokenize prompt
    std::vector<llama_token> tokens = common_tokenize(g_context, prompt, true, true);
    LOGi("Prompt token count: %zu", tokens.size());

    int n_ctx = llama_n_ctx(g_context);
    if ((int) tokens.size() >= n_ctx - 16) {
        LOGe("Prompt too long for context window (%zu tokens >= %d)", tokens.size(), n_ctx);
        return JNI_FALSE;
    }

    // Decode prompt tokens in batches
    for (size_t i = 0; i < tokens.size(); i += 512) {
        size_t batch_size = std::min((size_t) 512, tokens.size() - i);
        g_batch.clear();
        for (size_t j = 0; j < batch_size; j++) {
            llama_token tid = tokens[i + j];
            bool want_logits = (i + j == tokens.size() - 1);
            g_batch.add(tid, (llama_pos)(i + j), 0, want_logits);
        }

        if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
            LOGe("llama_process decode failed during prompt processing");
            return JNI_FALSE;
        }
    }

    g_current_position = (int) tokens.size();
    int predict_limit = max_tokens > 0 ? max_tokens : 512;
    g_max_predict_position = std::min(n_ctx - 4, g_current_position + predict_limit);

    LOGi("Prompt ingested. Current pos: %d, Max pos: %d", g_current_position, g_max_predict_position);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeNextToken(
        JNIEnv *env,
        jobject /* thiz */) {
    if (!g_model || !g_context || !g_sampler) {
        return nullptr;
    }

    if (g_current_position >= g_max_predict_position) {
        LOGi("Reached maximum token prediction position");
        return nullptr;
    }

    // Sample next token
    llama_token token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, token_id, true);

    // Stop if token is end-of-generation / EOS
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token_id)) {
        LOGi("Encountered EOG token (%d). Ending generation.", token_id);
        return nullptr;
    }

    // Append and decode this token
    g_batch.clear();
    g_batch.add(token_id, g_current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("llama_process failed while decoding token %d", token_id);
        return nullptr;
    }
    g_current_position++;

    // Convert token to text piece
    std::string piece = common_token_to_piece(g_context, token_id);
    g_cached_token_chars += piece;

    if (is_valid_utf8(g_cached_token_chars.c_str())) {
        jstring result = env->NewStringUTF(g_cached_token_chars.c_str());
        g_cached_token_chars.clear();
        return result;
    } else {
        // Multi-byte UTF8 incomplete piece, return empty string for now and buffer
        return env->NewStringUTF("");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeStopCompletion(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    g_max_predict_position = g_current_position;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeUnload(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    LOGi("Unloading model and freeing resources");
    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_batch = common_batch();
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_cached_token_chars.clear();
    g_current_position = 0;
    g_max_predict_position = 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_offlineai_assistant_llm_LlamaCppManager_nativeGetSystemInfo(
        JNIEnv *env,
        jobject /* thiz */) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "Unknown");
}
