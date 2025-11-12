import java.util.HashMap;

public class autenticathionManager {
        private final HashMap<String, String> userCredentials;

        public autenticathionManager() {
            this.userCredentials = new HashMap<>();
        }
        
        public boolean register(String username, String password) {
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                return false;
            }
    
            String hashedPassword = hashPassword(password);
            return users.putIfAbsent(username, hashedPassword) == null;
        }

        public boolean authenticate(String username, String password) {
            if (username == null || password == null) {
                return false;
            }
    
            String storedHash = users.get(username);
            if (storedHash == null) {
                return false;
            }
            return storedHash.equals(password);
        }

        public boolean userExists(String username) {
            return users.containsKey(username);
        }

        //pode ser útil para debug
        public int totalUsers() {
            return users.size();
        }
}
