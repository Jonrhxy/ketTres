package com.example.cverdetotoo;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Bfast1Fragment extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "Bfast1Fragment";

    private TextView textViewResult;
    private TextView textViewScore;
    private ImageView imageViewInput;
    private Button buttonTakePhoto;
    private Button buttonUploadImage;  // Button for gallery upload

    // ImageView for your character (shown/hidden as needed)
    private ImageView imageViewCharacter;
    // TextView for chat bubble
    private TextView textViewChatBubble;

    private Interpreter tflite;
    private List<String> labels;

    // Model input details
    private static final int MODEL_INPUT_WIDTH = 224;
    private static final int MODEL_INPUT_HEIGHT = 224;
    private static final int MODEL_PIXEL_SIZE = 3;    // RGB
    private static final int BYTES_PER_CHANNEL = 4;   // float32
    // Number of classes in your model
    private static final int MODEL_OUTPUT_CLASSES = 55;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    private int score = 0;
    // Flag to ensure Firestore is updated only once when score >= 50 (if needed)
    private boolean firestorePointsAdded = false;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw behind system bars for a full-screen look.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_bfast1);

        // Initialize UI elements.
        textViewResult = findViewById(R.id.textViewResult);
        textViewScore = findViewById(R.id.textViewScore);
        imageViewInput = findViewById(R.id.imageViewInput);
        buttonTakePhoto = findViewById(R.id.buttonTakePhoto);
        buttonUploadImage = findViewById(R.id.buttonUploadImage);
        imageViewCharacter = findViewById(R.id.imageViewCharacter);
        textViewChatBubble = findViewById(R.id.textViewChatBubble);

        // Initially hide the character.
        imageViewCharacter.setVisibility(View.GONE);

        // Set initial score.
        textViewScore.setText("Score: " + score);

        // Load the TFLite model.
        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(this, "model.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
            textViewResult.setText("Error loading TFLite model.");
            return;
        }

        // Load labels from assets (labels.txt).
        labels = loadLabels("labels.txt");
        if (labels.isEmpty()) {
            textViewResult.setText("No labels found. Make sure labels.txt is in assets.");
        }

        // Set up camera launcher.
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        Bitmap capturedImage = (Bitmap) extras.get("data");
                        imageViewInput.setImageBitmap(capturedImage);

                        // Show the character now that the photo is taken.
                        imageViewCharacter.setVisibility(View.VISIBLE);
                        bounceCharacter(imageViewCharacter);

                        // Show chat bubble for 2 seconds, then hide the character.
                        showChatBubble("Great shot! Let me see what food that is...", true, 2000);

                        processTestImage(capturedImage);
                    }
                }
        );

        // Set up gallery launcher for image selection.
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            imageViewInput.setImageBitmap(bitmap);

                            // Show the character for uploaded image.
                            imageViewCharacter.setVisibility(View.VISIBLE);
                            bounceCharacter(imageViewCharacter);

                            showChatBubble("Great upload! Let me see what food that is...", true, 2000);
                            processTestImage(bitmap);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        // Greet the user when the app opens.
        imageViewCharacter.setVisibility(View.VISIBLE);
        bounceCharacter(imageViewCharacter);
        showChatBubble("Welcome! Let's work together for a low-carbon lunch!", true, 5000);

        // Button click: launch camera after checking for permission.
        buttonTakePhoto.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(Bfast1Fragment.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        Bfast1Fragment.this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_REQUEST_CODE
                );
            } else {
                launchCamera();
            }
        });

        // Button click: launch gallery to pick an image.
        buttonUploadImage.setOnClickListener(v -> galleryLauncher.launch("image/*"));
    }

    // Launches the camera intent.
    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(cameraIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Starts a simple bounce animation for the character.
     */
    private void bounceCharacter(View view) {
        view.clearAnimation();
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "translationY", 0f, -30f);
        animator.setDuration(1000); // 1 second
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.start();
    }

    /**
     * Displays a chat bubble with the given message.
     */
    private void showChatBubble(String message, boolean hideCharacterAfter, int showDurationMs) {
        textViewChatBubble.setText(message);
        textViewChatBubble.setAlpha(0f);
        textViewChatBubble.setVisibility(View.VISIBLE);
        textViewChatBubble.animate().alpha(1f).setDuration(300).start();

        new Handler().postDelayed(() -> {
            textViewChatBubble.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                textViewChatBubble.setVisibility(View.INVISIBLE);
                if (hideCharacterAfter) {
                    imageViewCharacter.setVisibility(View.GONE);
                }
            }).start();
        }, showDurationMs);
    }

    /**
     * Processes the captured image by running the TFLite model for classification,
     * updating the score, and updating Firestore (only the CYCF document).
     * (BattleEco updates should be handled separately in your game logic.)
     */
    private void processTestImage(Bitmap bitmap) {
        float[] probabilities = classifyImageProbabilities(bitmap);

        // Find the highest probability.
        int maxIndex = 0;
        float maxProb = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                maxIndex = i;
            }
        }
        String predictedLabel = labels.get(maxIndex);

        String resultText;
        int pointsAwarded;

        // Switch-case logic based on model classes.
        switch (predictedLabel.toLowerCase()) {
            case "rice":
                resultText = "Rice detected!\nCarbon Emission: 0.5 kg CO₂-e per serving";
                pointsAwarded = 10;
                break;
            case "plate":
                resultText = "Plate detected!\n(No carbon emission data available)";
                pointsAwarded = 0;
                break;
            case "orange":
                resultText = "Orange detected!\nCarbon Emission: 0.4 kg CO₂-e per serving";
                pointsAwarded = 12;
                break;
            case "banana":
                resultText = "Banana detected!\nCarbon Emission: 0.2 kg CO₂-e per serving";
                pointsAwarded = 15;
                break;
            case "apple":
                resultText = "Apple detected!\nCarbon Emission: 0.3 kg CO₂-e per serving";
                pointsAwarded = 14;
                break;
            case "mango":
                resultText = "Mango detected!\nCarbon Emission: 0.6 kg CO₂-e per serving";
                pointsAwarded = 8;
                break;
            case "fried chicken":
                resultText = "Fried Chicken detected!\nCarbon Emission: 2.4 kg CO₂-e per serving";
                pointsAwarded = 2;
                break;
            case "tempura":
                resultText = "Tempura detected!\nCarbon Emission: 1.8 kg CO₂-e per serving";
                pointsAwarded = 3;
                break;
            case "fried fish":
                resultText = "Fried Fish detected!\nCarbon Emission: 1.1 kg CO₂-e per serving";
                pointsAwarded = 7;
                break;
            case "meat":
                resultText = "Meat detected!\nCarbon Emission: 3.5 kg CO₂-e per serving";
                pointsAwarded = 1;
                break;
            default:
                resultText = "Prediction: " + predictedLabel + "\n(No custom match found.)";
                pointsAwarded = 0;
                break;
        }

        // Update score.
        score += pointsAwarded;
        textViewScore.setText("Score: " + score);

        // Save points to SharedPreferences.
        SharedPreferences gamePrefs = getSharedPreferences("GameStats", MODE_PRIVATE);
        SharedPreferences.Editor editor = gamePrefs.edit();
        editor.putString("cycf_points", "Total Points: " + score);
        editor.apply();

        // Prepare date string.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String dateString = sdf.format(new Date());  // e.g. "2025-03-13 08:28"

        // Update only the CYCF document in Firestore.
        storeCYCFData(predictedLabel, pointsAwarded, 10, dateString);

        // Build a string with the top 3 predictions for reference.
        Integer[] sortedIndices = new Integer[MODEL_OUTPUT_CLASSES];
        for (int i = 0; i < MODEL_OUTPUT_CLASSES; i++) {
            sortedIndices[i] = i;
        }
        Arrays.sort(sortedIndices, (i1, i2) -> Float.compare(probabilities[i2], probabilities[i1]));
        StringBuilder sb = new StringBuilder();
        int topK = 3;  // Top 3 predictions
        for (int i = 0; i < topK; i++) {
            int idx = sortedIndices[i];
            float percent = probabilities[idx] * 100;
            sb.append(labels.get(idx)).append(": ")
                    .append(String.format("%.1f", percent)).append("%\n");
        }
        resultText += "\n\nPredictions:\n" + sb.toString();
        textViewResult.setText(resultText);

        // Set character expression and show final message.
        if (pointsAwarded > 0) {
            imageViewCharacter.setImageResource(R.drawable.main_character); // Smiling image
            showChatBubble("Awesome! You earned " + pointsAwarded + " points!", true, 2000);
        } else {
            imageViewCharacter.setImageResource(R.drawable.mc2); // Sad image
            showChatBubble("Hmm, that item might not be in my database yet!", true, 2000);
        }
    }

    /**
     * Updates the CYCF document in Firestore with the image processing results.
     * (Note: BattleEco updates are handled elsewhere.)
     */
    private void storeCYCFData(String predictedLabel, long points, long coins, String dateString) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String username = "unknown";
        if (currentUser != null) {
            username = currentUser.getDisplayName();
            if (username == null || username.isEmpty()) {
                username = currentUser.getUid();
            }
        }

        Map<String, Object> cycfData = new HashMap<>();
        cycfData.put("foodItemDetected", predictedLabel);
        cycfData.put("points", points);
        cycfData.put("coins", coins);
        cycfData.put("date", dateString);
        cycfData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("Gamez")
                .document(username)
                .collection("records")
                .document("CYCF")
                .set(cycfData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "CYCF updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update CYCF: " + e.getMessage()));
    }

    /**
     * Runs classification on the given Bitmap using the TFLite model.
     */
    private float[] classifyImageProbabilities(Bitmap bitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
        float[][] output = new float[1][MODEL_OUTPUT_CLASSES];
        tflite.run(inputBuffer, output);
        return output[0];
    }

    /**
     * Converts the input Bitmap to a ByteBuffer for the TFLite model.
     */
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, true);
        ByteBuffer floatBuffer = ByteBuffer.allocateDirect(
                MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT * MODEL_PIXEL_SIZE * BYTES_PER_CHANNEL
        );
        floatBuffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT];
        resized.getPixels(pixels, 0, MODEL_INPUT_WIDTH, 0, 0, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT);
        for (int pixel : pixels) {
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;
            floatBuffer.putFloat(r);
            floatBuffer.putFloat(g);
            floatBuffer.putFloat(b);
        }
        return floatBuffer;
    }

    /**
     * Loads labels from the specified file in assets (labels.txt).
     */
    private List<String> loadLabels(String fileName) {
        List<String> labelList = new ArrayList<>();
        try (InputStream is = getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                labelList.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return labelList;
    }

    // Optional: Applies edge detection using OpenCV.
    private Bitmap applyEdgeDetection(Bitmap bitmap) {
        Mat img = new Mat();
        Utils.bitmapToMat(bitmap, img);
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(img, img, new Size(5, 5), 0);
        Imgproc.Canny(img, img, 50, 150);
        Bitmap resultBitmap = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, resultBitmap);
        return resultBitmap;
    }
}
