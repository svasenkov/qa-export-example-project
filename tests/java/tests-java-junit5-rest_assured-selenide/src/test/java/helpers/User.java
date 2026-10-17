package helpers;

/**
 * Throwaway test identity for register / delete-account.
 */
public record User(String username, String password) {

    public String welcomeMessage() {
        return "Welcome, " + username + "!";
    }
}
