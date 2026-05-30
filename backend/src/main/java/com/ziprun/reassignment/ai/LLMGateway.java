package com.ziprun.reassignment.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles the provider-specific HTTP wire format for Gemini Flash,
 * OpenAI, Groq, and Ollama. Returns the raw text content of the model's
 * response as a String.
 *
 * What this handles: authentication headers, request body shape,
 * and response unwrapping for all three supported providers.
 *
 * What remains yours: prompt construction, JSON parsing of the
 * returned String, agent ID validation, fallback logic, and the
 * distinction between your initial and re-plan calls.
 */
@Component
@Slf4j
public class LLMGateway {

    @Value("${llm.provider}")
    private String provider;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    private final RestClient http = RestClient.create();

    /**
     * Sends prompt to the configured LLM and returns the raw text
     * response. Throws RuntimeException on HTTP error or unparseable
     * response — caller is responsible for fallback handling.
     */
    public String callLLM(String prompt) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> callGemini(prompt);
            case "openai" -> callOpenAICompatible(prompt,
                    baseUrl + "/v1/chat/completions");
            case "groq" -> callOpenAICompatible(prompt,
                    baseUrl + "/openai/v1/chat/completions");
            case "ollama" -> callOpenAICompatible(prompt,
                    baseUrl + "/v1/chat/completions");
            default -> throw new IllegalStateException(
                    "Unknown LLM provider: " + provider);
        };
    }

    private String callGemini(String prompt) {
        var url = baseUrl
                + "/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        var body = Map.of("contents", List.of(
                Map.of("parts", List.of(
                        Map.of("text", prompt)))));

        var resp = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        try {
            var candidates = (List<?>) resp.get("candidates");
            var content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            var parts = (List<?>) content.get("parts");
            return (String) ((Map<?, ?>) parts.get(0)).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Gemini response parse failed", e);
        }
    }

    private String callOpenAICompatible(String prompt, String url) {
        var body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt)));

        var resp = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        try {
            var choices = (List<?>) resp.get("choices");
            var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("LLM response parse failed", e);
        }
    }

    /**
     * Streams LLM response token by token.
     * Calls the tokenConsumer for each token received.
     * For hackathon: simulates streaming by chunking non-streaming response.
     */
    public void callLLMStreaming(String prompt, Consumer<String> tokenConsumer) {
        switch (provider.toLowerCase()) {
            case "gemini" -> callGeminiStreaming(prompt, tokenConsumer);
            case "openai" -> callOpenAIStreaming(prompt, tokenConsumer);
            case "groq" -> callGroqStreaming(prompt, tokenConsumer);
            case "ollama" -> callOllamaStreaming(prompt, tokenConsumer);
            default -> throw new IllegalStateException("Unknown provider: " + provider);
        }
    }

    private void callGeminiStreaming(String prompt, Consumer<String> tokenConsumer) {
        // Gemini supports streaming but for simplicity, simulate it
        String fullResponse = callGemini(prompt);
        simulateStreaming(fullResponse, tokenConsumer);
    }

    private void callOpenAIStreaming(String prompt, Consumer<String> tokenConsumer) {
        String fullResponse = callOpenAICompatible(prompt, baseUrl + "/v1/chat/completions");
        simulateStreaming(fullResponse, tokenConsumer);
    }

    private void callGroqStreaming(String prompt, Consumer<String> tokenConsumer) {
        String fullResponse = callOpenAICompatible(prompt, baseUrl + "/openai/v1/chat/completions");
        simulateStreaming(fullResponse, tokenConsumer);
    }

    private void callOllamaStreaming(String prompt, Consumer<String> tokenConsumer) {
        String fullResponse = callOpenAICompatible(prompt, baseUrl + "/v1/chat/completions");
        simulateStreaming(fullResponse, tokenConsumer);
    }

    private void simulateStreaming(String response, Consumer<String> tokenConsumer) {
        // Simulate token-by-token streaming for demo purposes
        // Split by words and send with small delays
        String[] words = response.split("(?<=\\s)");
        for (String word : words) {
            tokenConsumer.accept(word);
            try {
                Thread.sleep(30); // Small delay to simulate streaming
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Streaming interrupted");
                break;
            }
        }
    }
}
