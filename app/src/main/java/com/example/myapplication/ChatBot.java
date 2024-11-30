package com.example.myapplication;


import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.FutureCallback;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChatBot {

    private GenerativeModelFutures model;

    public ChatBot(String apiKey) {
        GenerativeModel gm = new GenerativeModel("tunedModels/trfdataproperwala-osepj4e3ar11", apiKey);
        model = GenerativeModelFutures.from(gm);
    }

    public void generateResponse(String userInput, ResponseCallback callback) {
        Content content = new Content.Builder().addText(userInput).build();

        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = result.getText();
                int inputTokens = estimateTokenCount(userInput);
                int outputTokens = estimateTokenCount(resultText);
                int totalTokens = inputTokens + outputTokens;
                callback.onResponse(resultText, totalTokens);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onError(t);
            }
        }, executor);
    }
    private int estimateTokenCount(String text) {
        return text.split("\\s+").length;  // Rough estimate: each word as a token
    }

    public interface ResponseCallback {
        void onResponse(String response,int tokensUsed);
        void onError(Throwable t);
    }
}
