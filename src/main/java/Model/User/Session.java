package Model.User;

/**
 * Simple in-memory session holder for desktop MVC apps.
 *
 * NOTE: This is NOT a web session. It just keeps track of the currently
 * authenticated user while the application is running.
 */
public final class Session {

    private static volatile User currentUser;

    private Session() {
        throw new UnsupportedOperationException("Utility class, cannot be instantiated");
    }

    /**
     * Marks the user as logged in for the current app runtime.
     */
    public static void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }
        currentUser = user;
    }

    /**
     * Clears the current session (logout).
     */
    public static void logout() {
        currentUser = null;
    }

    /**
     * @return true if there is a logged-in user.
     */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * @return the currently logged-in user, or null if nobody is logged in.
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Convenience method.
     * @return the logged-in user id, or -1 if not logged in.
     */
    public static int getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : -1;
    }
}
