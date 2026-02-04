package org.example.sistema_gestion_vitalexa.util;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utility class to handle unified user accounts.
 * NinaTorres and YicelaSandoval are treated as a single entity across the
 * system.
 * All their data (clients, orders, payments, reports) is shared between them.
 */
public class UserUnificationUtil {

    // Shared user constants
    public static final String NINA_TORRES = "NinaTorres";
    public static final String YICELA_SANDOVAL = "YicelaSandoval";

    // Pre-computed lists for efficiency
    public static final List<String> SHARED_USERNAMES = List.of(NINA_TORRES, YICELA_SANDOVAL);
    public static final List<String> SHARED_USERNAMES_LOWER = List.of(
            NINA_TORRES.toLowerCase(),
            YICELA_SANDOVAL.toLowerCase());

    /**
     * Check if a username is one of the shared users
     * 
     * @param username Username to check
     * @return true if username is NinaTorres or YicelaSandoval (case-insensitive)
     */
    public static boolean isSharedUser(String username) {
        if (username == null) {
            return false;
        }
        return SHARED_USERNAMES_LOWER.contains(username.toLowerCase());
    }

    /**
     * Get the list of usernames to query for a given username.
     * If the username is one of the shared users, returns both usernames.
     * Otherwise, returns a list with just the single username.
     * 
     * @param username Username to get shared usernames for
     * @return List of usernames to query
     */
    public static List<String> getSharedUsernames(String username) {
        if (isSharedUser(username)) {
            return SHARED_USERNAMES;
        }
        return List.of(username);
    }

    /**
     * Get the list of user IDs to query for a given user.
     * If the user is one of the shared users, returns both user IDs.
     * Otherwise, returns a list with just the single user ID.
     * 
     * This is useful for ID-based queries when you already have User objects.
     * 
     * @param userId      User ID
     * @param username    Username of the user
     * @param otherUserId The other shared user's ID (if applicable)
     * @return List of user IDs to query
     */
    public static List<UUID> getSharedUserIds(UUID userId, String username, UUID otherUserId) {
        if (isSharedUser(username) && otherUserId != null) {
            return Arrays.asList(userId, otherUserId);
        }
        return List.of(userId);
    }

    private UserUnificationUtil() {
        // Private constructor to prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
