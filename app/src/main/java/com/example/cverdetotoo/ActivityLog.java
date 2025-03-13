package com.example.cverdetotoo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ActivityLog - fetches logs from BattleEco, CYCF, and trackingwalk,
 * merges them in a chronological list, and displays them in one place.
 */
public class ActivityLog extends AppCompatActivity {

    private static final String TAG = "ActivityLog";

    // UI
    private LinearLayout logContainer;
    private TextView textDate;

    // Firestore + refresh
    private FirebaseFirestore db;
    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_activity_log);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Date in header (e.g., "March 13")
        textDate = findViewById(R.id.textDate);
        String currentDate = new SimpleDateFormat("MMMM dd", Locale.getDefault()).format(new Date());
        textDate.setText(currentDate);

        // Container for log cards
        logContainer = findViewById(R.id.logContainer);

        // Firestore
        db = FirebaseFirestore.getInstance();

        // Current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        final String displayName = (currentUser != null && currentUser.getDisplayName() != null)
                ? currentUser.getDisplayName() : "unknown";

        // Fetch logs initially
        updateLogs(displayName);

        // Refresh every 5 minutes (300000 ms)
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateLogs(displayName);
                refreshHandler.postDelayed(this, 300000);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 300000);
    }

    /**
     * Fetch logs from BattleEco, CYCF, and trackingwalk, merge them, and display.
     */
    private void updateLogs(String displayName) {
        // Clear UI
        logContainer.removeAllViews();

        // We'll collect everything in a single list
        final List<LogEntry> allLogs = new ArrayList<>();

        // 1) Fetch from "records" subcollection (BattleEco, CYCF, etc.)
        db.collection("Gamez")
                .document(displayName)
                .collection("records")
                .get()
                .addOnSuccessListener((QuerySnapshot queryDocumentSnapshots) -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String docId = doc.getId();
                        Log.d(TAG, "docId from records: " + docId);

                        if ("BattleEco".equals(docId)) {
                            // Always use the date string for BattleEco
                            // (Ignore timestamp to fix the 8:44am vs 9:31am mismatch)
                            Long timeClearedSec = doc.getLong("timeCleared");
                            if (timeClearedSec == null) timeClearedSec = 0L;

                            Long coins = doc.getLong("coins");
                            if (coins == null) coins = 0L;

                            // Parse only "date" (e.g. "2025-03-19 09:31")
                            String dateStr = doc.getString("date");
                            long timeMillis = parseDateToMillis(dateStr);

                            // Build a message
                            String timeClearedStr = formatSecondsToMinSec(timeClearedSec);
                            String timeAmPm = formatMillisToTimeAmPm(timeMillis);
                            String message = String.format(
                                    "%s you cleared the game in %s gained %d coins",
                                    timeAmPm, timeClearedStr, coins
                            );

                            allLogs.add(new LogEntry(timeMillis, message));

                        } else if ("CYCF".equals(docId)) {
                            // For CYCF, we can keep the existing logic: check timestamp or date
                            Long coins = doc.getLong("coins");
                            if (coins == null) coins = 0L;

                            Long points = doc.getLong("points");
                            if (points == null) points = 0L;

                            String foodItem = doc.getString("foodItemDetected");
                            if (foodItem == null) foodItem = "some food";

                            String dateStr = doc.getString("date");
                            Timestamp ts = doc.getTimestamp("timestamp");
                            long timeMillis = (ts != null)
                                    ? ts.toDate().getTime()
                                    : parseDateToMillis(dateStr);

                            String timeAmPm = formatMillisToTimeAmPm(timeMillis);
                            String message = String.format(
                                    "%s you took a photo of %s and gained %d points and %d coins",
                                    timeAmPm, foodItem, points, coins
                            );

                            allLogs.add(new LogEntry(timeMillis, message));
                        }
                        // ... handle more docs if you have them
                    }

                    // 2) Now fetch trackingwalk
                    fetchTrackingWalk(displayName, allLogs);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching records: " + e.getMessage());
                    Toast.makeText(ActivityLog.this, "Error fetching records.", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fetch docs from trackingwalk collection, build messages, merge into allLogs, then display.
     */
    private void fetchTrackingWalk(String displayName, List<LogEntry> allLogs) {
        db.collection("Games")
                .document(displayName)
                .collection("trackingwalk")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String docId = doc.getId();
                        Log.d(TAG, "trackingwalk doc: " + docId);

                        // Example fields
                        Long stepsSoFar = doc.getLong("stepsSoFar");
                        if (stepsSoFar == null) stepsSoFar = 0L;

                        Double co2Saved = doc.getDouble("co2Saved");
                        if (co2Saved == null) co2Saved = 0.0;

                        Timestamp ts = doc.getTimestamp("timestamp");
                        long timeMillis = (ts != null) ? ts.toDate().getTime() : System.currentTimeMillis();

                        // If you track a "started" or "goalReached" field, handle them
                        Boolean started = doc.getBoolean("started");
                        if (started != null && started) {
                            String msgStart = "You have started your tracking—reach 1500 steps to reduce CO₂ emission. Small step, big impact!";
                            String fullMsg = formatTimeFromTimestamp(ts) + " " + msgStart;
                            allLogs.add(new LogEntry(timeMillis, fullMsg));
                        }

                        Boolean goalReached = doc.getBoolean("goalReached");
                        if (goalReached != null && goalReached) {
                            String msgGoal = String.format("you have finished the goal of %d steps and reduced %.2f kg CO₂!", stepsSoFar, co2Saved);
                            String fullMsg = formatTimeFromTimestamp(ts) + " " + msgGoal;
                            allLogs.add(new LogEntry(timeMillis, fullMsg));
                        }

                        // Also add a generic log for the doc
                        String msgGeneric = String.format(
                                "you walked %d steps, saved %.2f kg CO₂",
                                stepsSoFar, co2Saved
                        );
                        String fullMsg = formatTimeFromTimestamp(ts) + " " + msgGeneric;
                        allLogs.add(new LogEntry(timeMillis, fullMsg));
                    }

                    // Sort all logs by timestamp
                    allLogs.sort(null);
                    // Display
                    displayLogs(allLogs);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching trackingwalk: " + e.getMessage());
                    Toast.makeText(ActivityLog.this, "Error fetching trackingwalk.", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayLogs(List<LogEntry> allLogs) {
        if (allLogs.isEmpty()) {
            addSimpleCard("", "No Records");
            return;
        }
        for (LogEntry entry : allLogs) {
            addSimpleCard("", entry.message);
        }
    }

    /**
     * Helper to add a card to the container.
     */
    private void addSimpleCard(String title, String content) {
        LayoutInflater inflater = LayoutInflater.from(this);
        MaterialCardView card = (MaterialCardView) inflater.inflate(R.layout.card_log_item, logContainer, false);
        TextView tvTitle = card.findViewById(R.id.cardHeaderTitle);
        TextView tvContent = card.findViewById(R.id.cardHeaderContent);
        tvTitle.setText(title);
        tvContent.setText(content);
        logContainer.addView(card);
    }

    /**
     * Always parse the date string for BattleEco to fix the mismatch.
     * For CYCF/trackingwalk, we can still rely on timestamp if present.
     *
     * This method tries "yyyy-MM-dd HH:mm:ss" then "yyyy-MM-dd HH:mm".
     * If both fail, returns current time.
     */
    private long parseDateToMillis(String dateStr) {
        if (dateStr == null) {
            return System.currentTimeMillis();
        }
        try {
            // Attempt with seconds
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date d1 = sdf1.parse(dateStr);
            return d1.getTime();
        } catch (Exception e) {
            // Fallback to no seconds
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date d2 = sdf2.parse(dateStr);
                return d2.getTime();
            } catch (Exception e2) {
                e2.printStackTrace();
                return System.currentTimeMillis();
            }
        }
    }

    /**
     * Convert a Firestore Timestamp to "8:20am" format.
     */
    private String formatTimeFromTimestamp(Timestamp ts) {
        if (ts == null) return "Unknown time";
        Date d = ts.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.getDefault());
        return sdf.format(d).toLowerCase();
    }

    /**
     * Convert parsed timeMillis into "8:44am" format.
     */
    private String formatMillisToTimeAmPm(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.getDefault());
        return sdf.format(new Date(timeMillis)).toLowerCase();
    }

    /**
     * Convert seconds -> "2:00mins" etc.
     */
    private String formatSecondsToMinSec(long totalSec) {
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format(Locale.getDefault(), "%d:%02dmins", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    /**
     * Inner class for storing a single log entry with a time and a message.
     */
    static class LogEntry implements Comparable<LogEntry> {
        long timestampMillis;
        String message;

        LogEntry(long timestampMillis, String message) {
            this.timestampMillis = timestampMillis;
            this.message = message;
        }

        @Override
        public int compareTo(LogEntry other) {
            // Sort ascending by time
            return Long.compare(this.timestampMillis, other.timestampMillis);
        }
    }
}
