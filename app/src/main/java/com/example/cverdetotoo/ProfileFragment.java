package com.example.cverdetotoo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cverdetotoo.CharacterModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView textCoinValue;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ImageView imageSelectedCharacter;
    private TextView textSelectedCharacterName;
    private RecyclerView recyclerUnlockedChars;
    private ImageView shopIcon;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Gift icon
        LinearLayout giftLayout = view.findViewById(R.id.giftLayout);
        giftLayout.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Gift")
                    .setMessage("You have a new gift!")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        // Settings icon
        FrameLayout settingsLayout = view.findViewById(R.id.settingsLayout);
        settingsLayout.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), Settings.class));
        });

        // "See all" clickable text for badges
        TextView textSeeAll = view.findViewById(R.id.textSeeAll);
        textSeeAll.setOnClickListener(v -> {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.constraintLayout, new BadgesFragment())
                    .commit();
        });

        // Greeting
        TextView textGreeting = view.findViewById(R.id.textGreeting);
        if (mAuth.getCurrentUser() != null) {
            String username = mAuth.getCurrentUser().getDisplayName();
            if (username != null && !username.isEmpty()) {
                DocumentReference userDocRef = db.collection("users").document(username);
                userDocRef.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String firstName = task.getResult().getString("firstName");
                        if (firstName != null && !firstName.isEmpty()) {
                            textGreeting.setText("Hi, " + firstName + "!");
                        } else {
                            textGreeting.setText("Hi!");
                        }
                    } else {
                        Toast.makeText(getActivity(), "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                        textGreeting.setText("Hi!");
                    }
                });
            } else {
                textGreeting.setText("Hi!");
            }
        } else {
            textGreeting.setText("Hi, Guest!");
        }

        // Clickable "Activity Log" bubble
        TextView activityLog = view.findViewById(R.id.textActivityLogHistoryTitle);
        activityLog.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ActivityLog.class);
            startActivity(intent);
        });

        // Day bubble selection
        LinearLayout activityLogLayout = view.findViewById(R.id.activityLogLayout);
        int childCount = activityLogLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View bubbleView = activityLogLayout.getChildAt(i);
            bubbleView.setOnClickListener(v -> {
                // Reset all bubbles
                for (int j = 0; j < activityLogLayout.getChildCount(); j++) {
                    View child = activityLogLayout.getChildAt(j);
                    child.setBackgroundResource(R.drawable.bgcircle_gray);
                }
                // Mark selected bubble
                v.setBackgroundResource(R.drawable.bgcircle_selected);
            });
        }

        // Update coin points
        textCoinValue = view.findViewById(R.id.textCoinValue);
        fetchCoinPoints();

        // Remove mini shop connection from coin icon
        ImageView coinIcon = view.findViewById(R.id.imageCoinIcon);
        coinIcon.setOnClickListener(null);

        // Shop icon setup
        shopIcon = view.findViewById(R.id.imageShop);
        shopIcon.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MiniShopActivity.class);
            startActivity(intent);
        });

        imageSelectedCharacter = view.findViewById(R.id.imageSelectedCharacter);
        textSelectedCharacterName = view.findViewById(R.id.textSelectedCharacterName);
        recyclerUnlockedChars = view.findViewById(R.id.recyclerUnlockedChars);

        // Load characters from SharedPreferences
        SharedPreferences prefs = requireActivity().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE);
        List<CharacterModel> allCharacters = loadCharactersFromStorage(prefs);

        // Show selected character
        String selectedCharacterId = prefs.getString("selectedCharacterId", "char001");
        CharacterModel selectedCharacter = findSelectedCharacter(allCharacters, selectedCharacterId);
        if (selectedCharacter != null) {
            imageSelectedCharacter.setImageResource(selectedCharacter.getImageResId());
            textSelectedCharacterName.setText(selectedCharacter.getName());
        }

        // Build unlocked list
        List<CharacterModel> unlockedList = new ArrayList<>();
        for (CharacterModel c : allCharacters) {
            if (c.isUnlocked()) {
                unlockedList.add(c);
            }
        }

        UnlockedCharAdapter adapter = new UnlockedCharAdapter(unlockedList, character -> {
            prefs.edit().putString("selectedCharacterId", character.getId()).apply();
            imageSelectedCharacter.setImageResource(character.getImageResId());
            textSelectedCharacterName.setText(character.getName());
        });
        recyclerUnlockedChars.setLayoutManager(
                new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
        recyclerUnlockedChars.setAdapter(adapter);

        // Optional bottom button
        Button bottomButton = view.findViewById(R.id.bottomButton);
        bottomButton.setOnClickListener(v -> {
            Toast.makeText(getActivity(), "Bottom button clicked", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchCoinPoints() {
        if (mAuth.getCurrentUser() != null) {
            String username = mAuth.getCurrentUser().getDisplayName();
            if (username != null && !username.isEmpty()) {

                // We'll keep a running total in an array (so we can modify it in callbacks)
                final long[] totalCoins = {0};

                // 1) Fetch main doc: Games/[username]
                db.collection("Games")
                        .document(username)
                        .get()
                        .addOnSuccessListener(docSnap -> {
                            if (docSnap.exists()) {
                                Long mainCoins = docSnap.getLong("coins");
                                if (mainCoins != null) {
                                    totalCoins[0] += mainCoins;
                                }
                            }
                            // 2) Fetch BattleEco doc
                            db.collection("Gamez")
                                    .document(username)
                                    .collection("records")
                                    .document("BattleEco")
                                    .get()
                                    .addOnSuccessListener(battleSnap -> {
                                        if (battleSnap.exists()) {
                                            Long battleCoins = battleSnap.getLong("coins");
                                            if (battleCoins != null) {
                                                totalCoins[0] += battleCoins;
                                            }
                                        }
                                        // 3) Fetch CYCF doc
                                        db.collection("Gamez")
                                                .document(username)
                                                .collection("records")
                                                .document("CYCF")
                                                .get()
                                                .addOnSuccessListener(cycfSnap -> {
                                                    if (cycfSnap.exists()) {
                                                        Long cycfCoins = cycfSnap.getLong("coins");
                                                        if (cycfCoins != null) {
                                                            totalCoins[0] += cycfCoins;
                                                        }
                                                    }

                                                    // Finally, display the sum in textCoinValue
                                                    textCoinValue.setText(String.valueOf(totalCoins[0]));

                                                })
                                                .addOnFailureListener(e -> {
                                                    // If CYCF fails, show partial total
                                                    textCoinValue.setText(String.valueOf(totalCoins[0]));
                                                    Toast.makeText(getActivity(),
                                                            "Failed to fetch CYCF coins: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT
                                                    ).show();
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        // If BattleEco fails, skip it
                                        textCoinValue.setText(String.valueOf(totalCoins[0]));
                                        Toast.makeText(getActivity(),
                                                "Failed to fetch BattleEco coins: " + e.getMessage(),
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            // If main doc fails, default to 0
                            textCoinValue.setText("0");
                            Toast.makeText(getActivity(),
                                    "Failed to fetch main doc coins: " + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
            } else {
                // username is empty
                textCoinValue.setText("0");
            }
        } else {
            // no currentUser
            textCoinValue.setText("0");
        }
    }


    private List<CharacterModel> loadCharactersFromStorage(SharedPreferences prefs) {
        String json = prefs.getString("characters", null);
        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<CharacterModel>>() {}.getType();
            return gson.fromJson(json, type);
        } else {
            List<CharacterModel> list = new ArrayList<>();
            list.add(new CharacterModel("char001", "Green Warrior", R.drawable.main_character, 100, true));
            list.add(new CharacterModel("char002", "Solar Knight", R.drawable.character_solar, 200, false));
            list.add(new CharacterModel("char003", "Wind Mage", R.drawable.character_wind, 300, false));
            list.add(new CharacterModel("char004", "Nature Knight", R.drawable.character_nature, 250, false));
            return list;
        }
    }

    private CharacterModel findSelectedCharacter(List<CharacterModel> allChars, String selectedId) {
        for (CharacterModel c : allChars) {
            if (c.getId().equals(selectedId)) {
                return c;
            }
        }
        return null;
    }
}
