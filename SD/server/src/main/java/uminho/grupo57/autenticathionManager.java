package uminho.grupo57;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de autenticação thread-safe com persistência
 * Conta admin pré-definida: admin/1234
 */
public class autenticathionManager {
    private final ConcurrentHashMap<String, String> userCredentials;
    private final String usersFilePath;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "1234";

    public autenticathionManager() {
        this("data/users.dat");
    }

    public autenticathionManager(String usersFilePath) {
        this.userCredentials = new ConcurrentHashMap<>();
        this.usersFilePath = usersFilePath;
        
        // Adicionar conta admin por defeito
        userCredentials.put(ADMIN_USERNAME, ADMIN_PASSWORD);
        
        loadUsers();
    }
    
    //Verifica se o utilizador é administrador
    public boolean isAdmin(String username) {
        return ADMIN_USERNAME.equals(username);
    }
    
    //Regista novo utilizador e persiste
    public synchronized boolean register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        
        // Não permitir registo com username "admin"
        if (ADMIN_USERNAME.equals(username)) {
            return false;
        }

        boolean result = userCredentials.putIfAbsent(username, password) == null;
        if (result) {
            saveUsers();
        }
        return result;
    }

    // Autentica utilizador
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

    //Carrega utilizadores do ficheiro
    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> loaded = (ConcurrentHashMap<String, String>) ois.readObject();
            
            // Adicionar utilizadores carregados (exceto se for admin)
            for (var entry : loaded.entrySet()) {
                if (!ADMIN_USERNAME.equals(entry.getKey())) {
                    userCredentials.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            
            System.out.println("Carregados " + (userCredentials.size() - 1) + " utilizadores (+ admin)");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
        }
    }

    //Guarda utilizadores no ficheiro
    private synchronized void saveUsers() {
        File file = new File(usersFilePath);
        file.getParentFile().mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            // Guardar apenas utilizadores normais (não guardar admin)
            ConcurrentHashMap<String, String> toSave = new ConcurrentHashMap<>();
            for (var entry : userCredentials.entrySet()) {
                if (!ADMIN_USERNAME.equals(entry.getKey())) {
                    toSave.put(entry.getKey(), entry.getValue());
                }
            }
            oos.writeObject(toSave);
        } catch (IOException e) {
            System.err.println("Erro ao guardar utilizadores: " + e.getMessage());
        }
    }

    /**
    * Persiste todos os utilizadores (chamado externamente)
    */
    public void persistAll() {
        saveUsers();
    }
}
