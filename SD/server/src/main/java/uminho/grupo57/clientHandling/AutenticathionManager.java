package uminho.grupo57.clientHandling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor de autenticação thread-safe com persistência simples em ficheiro.
 * <p>Fornece registo, autenticação e persistência de credenciais. Conta
 * administrativa padrão: `admin/1234`.</p>
 */
public class AutenticathionManager {
    private HashMap<String, String> userCredentials = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final String usersFilePath;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "1234";

    private void addUser(String username, String password)
    {
        lock.lock();
        try{
            userCredentials.put(username, password);
        }finally{
            lock.unlock();
        }
    }

    /**
     * Cria gestor usando ficheiro por defeito `data/users.dat`.
     */
    public AutenticathionManager() {
        this("data/users.dat");
    }

    /**
     * Cria gestor com ficheiro de utilizadores especificado.
     *
     * @param usersFilePath Caminho para ficheiro de credenciais
     */
    public AutenticathionManager(String usersFilePath)
    {
        this.usersFilePath = usersFilePath;
        addUser(ADMIN_USERNAME, ADMIN_PASSWORD); // Adicionar conta admin por defeito
        loadUsers();
    }
    
    /**
     * Verifica se o username corresponde ao administrador.
     *
     * @param username Nome de utilizador
     * @return {@code true} se for admin
     */
    public boolean isAdmin(String username)
    {
        return ADMIN_USERNAME.equals(username);
    }
    
    /**
     * Regista novo utilizador e persiste as credenciais.
     *
     * @param username Nome do utilizador (não vazio, não "admin")
     * @param password Palavra-passe (não vazia)
     * @return {@code true} se registo efetuado, {@code false} se já existir ou inválido
     */
    public boolean register(String username, String password)
    {
        if(username == null || username.isEmpty() || password == null || password.isEmpty())
            return false;
        if(ADMIN_USERNAME.equals(username)) // Não permitir registo com username "admin"
            return false;

        lock.lock();
        try{
            boolean result = userCredentials.putIfAbsent(username, password) == null;
            if(result)
                saveUsers();
            return result;
        }finally{
            lock.unlock();
        }
    }

    /**
     * Autentica utilizador comparando credenciais armazenadas.
     *
     * @param username Nome do utilizador
     * @param password Palavra-passe
     * @return {@code true} se credenciais válidas
     */
    public boolean authenticate(String username, String password)
    {
        if(username == null || password == null)
            return false;

        String storedPassword;
        lock.lock();
        try{
            storedPassword = userCredentials.get(username);
        }finally{
            lock.unlock();
        }
        if(storedPassword == null)
            return false;
        return storedPassword.equals(password);
    }

    //Carrega utilizadores do ficheiro
    private void loadUsers()
    {
        File file = new File(usersFilePath);
        if(!file.exists())
            return;

        lock.lock();
        try{
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            int numEntries = in.readInt();

            for(int i=0; i<numEntries; i++)
            {
                String user = in.readUTF();
                String password = in.readUTF();
                if(!ADMIN_USERNAME.equals(user))
                    userCredentials.put(user, password);
            }
            System.out.println("Carregados " + (userCredentials.size() - 1) + " utilizadores (+ admin)");
        }catch (IOException e){
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
        }finally{
            lock.unlock();
        }
    }

    //Guarda utilizadores no ficheiro
    private void saveUsers()
    {
        File file = new File(usersFilePath);
        file.getParentFile().mkdirs();

        lock.lock();
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            out.writeInt(userCredentials.size());
            for(Map.Entry<String, String> entrada: userCredentials.entrySet())
            {
                if(!ADMIN_USERNAME.equals(entrada.getKey()))
                {
                    out.writeUTF(entrada.getKey());
                    out.writeUTF(entrada.getValue());
                }
            }
            out.flush();
        }catch (IOException e){
            System.err.println("Erro a guardar ficheiros: " + e.getMessage());
        }finally{
            lock.unlock();
        }
    }

    /**
     * Persiste todos os utilizadores no ficheiro configurado.
     */
    public void persistAll()
    {
        saveUsers();
    }
}
