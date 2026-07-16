package com.quickprint.guest;

import java.security.SecureRandom;

// Friendly names for guest uploads: adjective + animal + 2 digits, e.g.
// "sillygoose76". Words are all <= 6 chars so the result always fits the
// 20-char nickname slug. Uniqueness is the caller's job (retry on collision).
public final class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "silly", "happy", "brave", "calm", "sunny", "witty", "fuzzy",
            "jolly", "lucky", "merry", "noble", "perky", "quick", "shiny",
            "zesty", "bouncy", "chirpy", "dandy", "eager", "frosty",
    };

    private static final String[] ANIMALS = {
            "goose", "otter", "panda", "tiger", "koala", "gecko", "llama",
            "moose", "robin", "shark", "whale", "zebra", "bison", "camel",
            "dingo", "eagle", "ferret", "heron", "iguana", "jackal",
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private NicknameGenerator() {
    }

    public static String generate() {
        return ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)]
                + ANIMALS[RANDOM.nextInt(ANIMALS.length)]
                + (10 + RANDOM.nextInt(90));
    }
}
