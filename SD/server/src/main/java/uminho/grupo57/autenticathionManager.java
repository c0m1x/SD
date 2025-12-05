package uminho.grupo57;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de autenticação thread-safe com persistência
 */
public class autenticathionManager {
    private final ConcurrentHashMap<String, String> userCredentials;
    private final String usersFilePath;

    public autenticathionManager() {
        this("data/users.dat");
    }

    public autenticathionManager(String usersFilePath) {
        this.userCredentials = new ConcurrentHashMap<>();
        this.usersFilePath = usersFilePath;
        loadUsers();
    }
    
    /**
     * Regista novo utilizador e persiste
     * @return true se registou com sucesso, false se utilizador já existe
     */
    public synchronized boolean register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        boolean result = userCredentials.putIfAbsent(username, password) == null;
        if (result) {
            saveUsers();
        }
        return result;
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

    /**
     * Carrega utilizadores do disco
     */
    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) {
            System.out.println("Ficheiro de utilizadores não encontrado. Será criado ao registar o primeiro utilizador.");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> loaded = (ConcurrentHashMap<String, String>) ois.readObject();
            userCredentials.putAll(loaded);
            System.out.println("✓ Carregados " + userCredentials.size() + " utilizadores do disco");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
        }
    }

    /**
     * Guarda utilizadores no disco
     */
    private synchronized void saveUsers() {
        File file = new File(usersFilePath);
        file.getParentFile().mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(userCredentials);
        } catch (IOException e) {
            System.err.println("Erro ao guardar utilizadores: " + e.getMessage());
        }
    }

    /**
     * Persiste utilizadores (chamado no shutdown)
     */
    public void persistAll() {
        saveUsers();
        System.out.println("✓ Utilizadores persistidos: " + userCredentials.size());
    }
}
