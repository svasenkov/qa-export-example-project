package helpers;

/**
 * Throwaway test identity. Faker methods are for register / delete-account.
 */
public final class UserBuilder {

    private String username;
    private String password;

    public UserBuilder withUsername() {
        this.username = DataFaker.username();
        return this;
    }

    public UserBuilder withPassword() {
        this.password = DataFaker.password();
        return this;
    }

    public User build() {
        return new User(username, password);
    }
}
