package com.example.cverdetotoo;

public class BattleCard {

    private CardType type;
    private int effectValue;   // For healing/damage amounts.
    private String fact;       // The card's description/name.
    private int imageResId;    // Drawable resource for the card image.
    private int energyCost;    // New field for the card's energy cost.

    // Updated constructor that includes energyCost.
    public BattleCard(CardType type, int effectValue, String fact, int imageResId, int energyCost) {
        this.type = type;
        this.effectValue = effectValue;
        this.fact = fact;
        this.imageResId = imageResId;
        this.energyCost = energyCost;
    }

    public CardType getType() {
        return type;
    }

    public int getEffectValue() {
        return effectValue;
    }

    public String getFact() {
        return fact;
    }

    public int getImageResId() {
        return imageResId;
    }

    // Getter for energy cost.
    public int getEnergyCost() {
        return energyCost;
    }

    // This method is used as the card's "name" in the game.
    public String getName() {
        return fact;
    }
}
