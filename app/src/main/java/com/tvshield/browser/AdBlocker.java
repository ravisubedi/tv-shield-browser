package com.tvshield.browser;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AdBlocker {
    enum BlockType { NONE, AD, TRACKER }

    private final Set<String> adHosts = new HashSet<>();
    private final Set<String> trackerHosts = new HashSet<>();

    AdBlocker(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("blocklist.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.US);
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("tracker:")) trackerHosts.add(line.substring(8).trim());
                else if (line.startsWith("ad:")) adHosts.add(line.substring(3).trim());
                else adHosts.add(line);
            }
        } catch (Exception ignored) { }
    }

    BlockType getBlockType(Uri uri) {
        String host = uri.getHost();
        if (host == null) return BlockType.NONE;
        host = host.toLowerCase(Locale.US);
        if (matches(host, trackerHosts)) return BlockType.TRACKER;
        if (matches(host, adHosts)) return BlockType.AD;
        return BlockType.NONE;
    }

    private boolean matches(String host, Set<String> blockedHosts) {
        for (String blocked : blockedHosts)
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        return false;
    }
}
