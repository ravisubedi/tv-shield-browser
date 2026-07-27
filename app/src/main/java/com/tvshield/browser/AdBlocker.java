package com.tvshield.browser;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AdBlocker {
    private final Set<String> blockedHosts = new HashSet<>();

    AdBlocker(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("blocklist.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.US);
                if (!line.isEmpty() && !line.startsWith("#")) blockedHosts.add(line);
            }
        } catch (Exception ignored) { }
    }

    boolean shouldBlock(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        for (String blocked : blockedHosts) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }
}
