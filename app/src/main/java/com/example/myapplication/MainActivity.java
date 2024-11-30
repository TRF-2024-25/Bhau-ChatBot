package com.example.myapplication;

import org.json.JSONObject;
import java.io.InputStream;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.Intent;
import android.media.session.MediaSession;
import android.view.KeyEvent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MediaSession mediaSession;
    private ChatBot chatBot;
    private ImageView cambut;
    private ImageView gifView;;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private static final int REQUEST_MIC_PERMISSION = 1;
    private JSONObject predefinedPrompts;
    private TextView tokencount;
    int tokensUsed;
    private static final int DETECTION_REQUEST_CODE = 1;
    private boolean isSpeakerOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);

        initMediaSession();
        try {
            predefinedPrompts = loadPredefinedPrompts();
        } catch (Exception e) {
            e.printStackTrace();
        }

        gifView = findViewById(R.id.gifView);
        tokencount = findViewById(R.id.textView);
        String apiKey = BuildConfig.apiKey;
        ConstraintLayout rootLayout = findViewById(R.id.rootLayout);

        rootLayout.setOnClickListener(v -> {
            // Start audio input (trigger SpeechRecognizer)
            startVoiceInput();
        });

        cambut=findViewById(R.id.CameraBut);
        playIdleGif();


        chatBot = new ChatBot(apiKey);

        textToSpeech = new TextToSpeech(getApplicationContext(), i -> {
            if (i != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.UK);
                setMaleVoice();
            }
        });

        setupUtteranceListener();

        cambut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(MainActivity.this, CameraAct.class);
                startActivityForResult(intent, DETECTION_REQUEST_CODE);
            }
        });

        checkAndRequestPermissions();

        initSpeechRecognizer();

        // Create a BroadcastReceiver to listen for media button presses
        BroadcastReceiver mediaButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                    KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                            // Trigger voice input when the wired headset button is pressed
                            startVoiceInput();
                        }
                    }
                }
            }

        };

// Register the receiver for media button events
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        MediaButtonReceiver receiver = new MediaButtonReceiver();// No need for exported flag here

//        startWakeWordListening();

        ContextCompat.registerReceiver(
                this,          // Context
                receiver,      // Receiver
                filter,        // Intent filter
                ContextCompat.RECEIVER_NOT_EXPORTED // Protection level
        );
    }

    public void toggleAudioOutput(View view) {
        if (isSpeakerOn) {
            revertAudioOutput();
            isSpeakerOn = false;
            Toast.makeText(this, "Audio routed to Earpiece", Toast.LENGTH_SHORT).show();
        } else {
            routeAudioToSpeaker();
            isSpeakerOn = true;
            Toast.makeText(this, "Audio routed to Speaker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Check if the intent has the "START_VOICE_INPUT" flag
        if (intent.getBooleanExtra("START_VOICE_INPUT", false)) {
            startVoiceInput();
        }
    }


    private JSONObject loadPredefinedPrompts() throws Exception {
        InputStream is = getAssets().open("predefined_prompts.json");
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();

        String json = new String(buffer, "UTF-8");
        return new JSONObject(json);
    }

//    private Handler handler = new Handler();
//    private Runnable wakeWordRunnable = this::startWakeWordListening;

//    private void startWakeWordListeningWithDelay() {
//        handler.removeCallbacks(wakeWordRunnable); // Clear any pending calls
//        handler.postDelayed(wakeWordRunnable, 500); // 500ms delay, adjust as needed
//    }


    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERMISSION);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                startWakeWordListening();
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private void initSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d("SpeechRecognizer", "Ready for speech");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d("SpeechRecognizer", "Speech input started");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d("SpeechRecognizer", "Speech input ended");
                }

                @Override
                public void onError(int error) {
                    Log.e("SpeechRecognizer", "Error: " + error);
                    // Optionally, restart listening if desired
                    // startVoiceInput();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        processInput(spokenText); // Process recognized text
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
    }



    private void setMaleVoice() {
        Voice specificMaleVoice = null;
        if (textToSpeech != null) {
            for (Voice voice : textToSpeech.getVoices()) {
                if (voice.getLocale().equals(Locale.UK) && voice.getName().toLowerCase().contains("male"))
                {
                    specificMaleVoice = voice;
                    break;
                }
            }
        }

        if (specificMaleVoice != null) {
            textToSpeech.setVoice(specificMaleVoice);
        } else {
            Toast.makeText(this, "No male voice available. Using default voice.", Toast.LENGTH_SHORT).show();
        }
    }


//    private void startWakeWordListening() {
//        if (speechRecognizer != null) {
//            speechRecognizer.startListening(speechRecognizerIntent);
//        } else {
//            Log.e("SpeechRecognizer", "SpeechRecognizer instance is null");
//        }
//    }


    private void startVoiceInput() {
        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }
        speechRecognizer.startListening(speechRecognizerIntent);
    }


    private void processInput(String input) {
        // Play idle GIF as the bot starts listening
        playIdleGif();

        String response = getPredefinedResponse(input);

        if (response != null) {
            response = response.replaceAll("[#*]", "");
            playSpeakingGif(); // Switch to speaking GIF
            textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, "SPEAKING_ID");
        } else {
            chatBot.generateResponse(input, new ChatBot.ResponseCallback() {
                @Override
                public void onResponse(String response, int tokensUsed) {
                    runOnUiThread(() -> {
                        String cleanedResponse = response.replaceAll("[#*]", "");
                        playSpeakingGif(); // Switch to speaking GIF
                        textToSpeech.speak(cleanedResponse, TextToSpeech.QUEUE_FLUSH, null, "SPEAKING_ID");
                        updateTokenCount(tokensUsed);
                    });
                }

                @Override
                public void onError(Throwable t) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }


    private String getPredefinedResponse(String input) {
        try {
            JSONObject responses = predefinedPrompts.getJSONObject("responses");
            return responses.optString(input.toLowerCase(), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void displayBotResponse(String response) {
        response = response.replaceAll("[#*]", "");
        ChatMessage botMessage = new ChatMessage(response, false);


        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
    }



    @Override
    protected void onDestroy() {

        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (mediaSession != null) {
            mediaSession.release(); // Clean up the MediaSession when the activity is destroyed
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void updateTokenCount(int tokensUsed) {
        this.tokensUsed += tokensUsed;
        tokencount.setText("Tokens Consumed: " + this.tokensUsed);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                processInput(spokenText); // Directly process the spoken text
            }
        }

        if (requestCode == DETECTION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String detectedObjects = data.getStringExtra("detectedObjects");
            processInput(detectedObjects); // Use detected objects as input
        }
    }

    private void playSpeakingGif() {
        if (gifView != null) {
            Glide.with(this)
                    .asGif()
                    .load(R.raw.speaking) // Correct the resource ID
                    .into(gifView);
        }
    }

    private void playIdleGif() {
        if (gifView != null) {
            Glide.with(this)
                    .asGif()
                    .load(R.raw.listening) // Correct the resource ID
                    .into(gifView);
        }
    }


    private void setupUtteranceListener() {
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if ("SPEAKING_ID".equals(utteranceId)) {
                    runOnUiThread(() -> playSpeakingGif());
                }
            }

            @Override
            public void onDone(String utteranceId) {
                if ("SPEAKING_ID".equals(utteranceId)) {
                    runOnUiThread(() -> playIdleGif());
                }
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> playIdleGif());
            }
        });
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(this, "MyMediaSession");

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    // Trigger voice input
                    startVoiceInput();
                    return true;
                }
                return false;
            }
        });

        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mediaSession.setActive(true);
    }

    private void revertAudioOutput() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false); // Turn off speakerphone
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION); // Keep in communication mode
        }
    }


    private void routeAudioToSpeaker() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION); // Set communication mode
            audioManager.setSpeakerphoneOn(true); // Route audio to speaker
        }
    }

    public class MediaButtonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.d("MediaButtonReceiver", "Key code: " + keyEvent.getKeyCode());
                }
            }
        }
    }



}