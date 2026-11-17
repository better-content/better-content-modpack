package com.bettercontent.playtestsupport.client;

import java.util.HashSet;
import java.util.Set;

/** One-use ID allocation for one joined native client session; a completed hold cannot be replayed. */
final class HeldInputIdLedger {
    private final Set<String> used = new HashSet<>();
    synchronized void claim(String holdId) {
        if (holdId == null || !holdId.matches("[A-Za-z0-9._-]{1,64}") || used.size() >= 4096 ||
                !used.add(holdId))
            throw new IllegalArgumentException("unreviewed, repeated or excessive physical hold ID");
    }
}
