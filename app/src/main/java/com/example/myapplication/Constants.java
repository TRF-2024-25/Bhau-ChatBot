package com.example.myapplication;

public final class Constants {
    public static final String MODEL_PATH = "model.tflite";
    public static final String LABELS_PATH = "labels.txt";

    // Private constructor to prevent instantiation
    private Constants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
