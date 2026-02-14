package me.pats.pets.i18n;

import org.bukkit.entity.Player;

import java.util.Locale;

public enum Language {
    RU("ru"),
    EN("en");

    private final String id;

    Language(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Language fromId(String id) {
        if (id == null) return null;
        for (Language lang : values()) {
            if (lang.id.equalsIgnoreCase(id)) return lang;
        }
        return null;
    }

    public static Language fromPlayerLocale(Player player) {
        if (player == null) return EN;
        Locale locale = player.locale();
        if (locale == null) return EN;
        String lang = locale.getLanguage();
        if (lang == null) return EN;
        return lang.equalsIgnoreCase("ru") ? RU : EN;
    }
}

