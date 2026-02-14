package me.pats.pets.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {}

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(input);
    }
}

