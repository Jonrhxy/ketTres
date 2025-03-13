package com.example.cverdetotoo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MiniShopActivity extends AppCompatActivity {

    private TextView textShopTitle, textUserCoins;
    private RecyclerView recyclerCharacters;
    private List<CharacterModel> characterList;
    private int userCoins;
    private SharedPreferences prefs;
    private FirebaseFirestore db;

    // Declare adapter as a field
    private CharacterShopAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mini_shop_activity);

        textShopTitle = findViewById(R.id.textShopTitle);
        textUserCoins = findViewById(R.id.textUserCoins);
        recyclerCharacters = findViewById(R.id.recyclerCharacters);

        prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();

        // Load local coins (default: 500)
        userCoins = prefs.getInt("coins", 500);
        textUserCoins.setText("Coins: " + userCoins);

        // Fetch coins from multiple Firestore locations and update UI:
        fetchCoinPoints();

        // Load the character list from SharedPreferences (merging missing defaults if needed)
        characterList = loadCharactersFromStorage();

        recyclerCharacters.setLayoutManager(new LinearLayoutManager(this));

        // Get the currently selected character ID (or default to "char001")
        String selectedCharacterId = prefs.getString("selectedCharacterId", "char001");

        // Initialize adapter as a field
        adapter = new CharacterShopAdapter(characterList, userCoins, selectedCharacterId, new CharacterShopAdapter.OnCharacterActionListener() {
            @Override
            public void onBuyClicked(CharacterModel character) {
                if (userCoins >= character.getCost()) {
                    int cost = character.getCost();
                    userCoins -= cost;
                    textUserCoins.setText("Coins: " + userCoins);
                    prefs.edit().putInt("coins", userCoins).apply();

                    // Deduct points from Firestore using FieldValue.increment(-cost)
                    db.collection("Games").document("Jonr")
                            .update("points", FieldValue.increment(-cost))
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(MiniShopActivity.this, "Purchased " + character.getName(), Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(MiniShopActivity.this, "Error updating coins", Toast.LENGTH_SHORT).show()
                            );

                    // Mark the character as unlocked and update storage
                    character.setUnlocked(true);
                    saveCharactersToStorage(characterList);

                    // Refresh adapter so the item now shows "Equip"
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(MiniShopActivity.this, "Not enough coins!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSelectClicked(CharacterModel character) {
                // Mark this character as selected in SharedPreferences
                prefs.edit().putString("selectedCharacterId", character.getId()).apply();
                Toast.makeText(MiniShopActivity.this, character.getName() + " equipped!", Toast.LENGTH_SHORT).show();
                // Update the adapter's selectedCharacterId and refresh the list
                adapter.setSelectedCharacterId(character.getId());
                adapter.notifyDataSetChanged();
                finish(); // Return to previous screen
            }
        });
        recyclerCharacters.setAdapter(adapter);
    }

    /**
     * Fetches coins from three sources:
     * 1. From Games/[username] document (field: coins)
     * 2. From Gamez/[username]/records/BattleEco document (field: coins)
     * 3. From Gamez/[username]/records/CYCF document (field: coins)
     * and then sums them to update textUserCoins.
     */
    private void fetchCoinPoints() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String username = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (username != null && !username.isEmpty()) {
                final long[] totalCoins = {0};

                // 1) Fetch from Games/[username] document
                db.collection("Games").document(username)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                Long mainCoins = documentSnapshot.getLong("coins");
                                if (mainCoins != null) {
                                    totalCoins[0] += mainCoins;
                                }
                            }
                            // 2) Fetch from Gamez/[username]/records/BattleEco document
                            db.collection("Gamez").document(username)
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
                                        // 3) Fetch from Gamez/[username]/records/CYCF document
                                        db.collection("Gamez").document(username)
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
                                                    // Update UI with the combined total
                                                    userCoins = (int) totalCoins[0];
                                                    textUserCoins.setText("Coins: " + userCoins);
                                                    prefs.edit().putInt("coins", userCoins).apply();
                                                })
                                                .addOnFailureListener(e -> {
                                                    textUserCoins.setText(String.valueOf(totalCoins[0]));
                                                    Toast.makeText(MiniShopActivity.this, "Error fetching CYCF coins: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        textUserCoins.setText(String.valueOf(totalCoins[0]));
                                        Toast.makeText(MiniShopActivity.this, "Error fetching BattleEco coins: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            textUserCoins.setText("0");
                            Toast.makeText(MiniShopActivity.this, "Error fetching main coins: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                textUserCoins.setText("0");
            }
        } else {
            textUserCoins.setText("0");
        }
    }

    /**
     * Returns the full list of default characters.
     */
    private List<CharacterModel> getDefaultCharacters() {
        List<CharacterModel> defaults = new ArrayList<>();
        defaults.add(new CharacterModel("char01", "Green Warrior", R.drawable.main_character, 100, true));  // unlocked by default
        defaults.add(new CharacterModel("char02", "Solar Knight", R.drawable.character_solar, 200, false));
        defaults.add(new CharacterModel("char03", "Wind Mage", R.drawable.character_wind, 300, false));
        defaults.add(new CharacterModel("char04", "Nature Knight", R.drawable.character_nature, 250, false));
        defaults.add(new CharacterModel("char05", "Zephyr Elves", R.drawable.character_zephyr, 150, false));
        defaults.add(new CharacterModel("char06", "Solis Earth Hero", R.drawable.character_solis, 150, false));
        defaults.add(new CharacterModel("char07", "Aero Air Guardian", R.drawable.character_aeron, 150, false));
        return defaults;
    }

    /**
     * Loads the character list from SharedPreferences.
     * If stored data exists, it will merge any missing default characters.
     */
    private List<CharacterModel> loadCharactersFromStorage() {
        List<CharacterModel> storedList;
        String json = prefs.getString("characters", null);
        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<CharacterModel>>() {}.getType();
            storedList = gson.fromJson(json, type);
        } else {
            storedList = new ArrayList<>();
        }

        // Get the new default list.
        List<CharacterModel> defaultList = getDefaultCharacters();

        // Merge defaults: If a default character is missing from the stored list, add it.
        for (CharacterModel defaultChar : defaultList) {
            boolean found = false;
            for (CharacterModel storedChar : storedList) {
                if (storedChar.getId().equals(defaultChar.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                storedList.add(defaultChar);
            }
        }

        // Save the merged list back to SharedPreferences
        saveCharactersToStorage(storedList);
        return storedList;
    }

    private void saveCharactersToStorage(List<CharacterModel> list) {
        Gson gson = new Gson();
        String json = gson.toJson(list);
        prefs.edit().putString("characters", json).apply();
    }
}
