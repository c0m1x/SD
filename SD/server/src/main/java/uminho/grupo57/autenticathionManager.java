package uminho.grupo57;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de autenticação thread-safe
 */
public class autenticathionManager {
    private final ConcurrentHashMap<String, String> userCredentials;

    public autenticathionManager() {
        this.userCredentials = new ConcurrentHashMap<>();
    }
    
    /**
     * Regista novo utilizador
     * @return true se registou com sucesso, false se utilizador já existe
     */
    public synchronized boolean register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        return userCredentials.putIfAbsent(username, password) == null;
    }

    /**
     * Autentica utilizador
     */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        String storedPassword = userCredentials.get(username);
        if (storedPassword == null) {
            return false;
        }
        return storedPassword.equals(password);
    }

    public boolean userExists(String username) {
        return userCredentials.containsKey(username);
    }

    public int totalUsers() {
        return userCredentials.size();
    }
}