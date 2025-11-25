package uminho.grupo57;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestão de persistência de séries em disco
 * Guarda e carrega DayData de/para disco
 */
public class SeriesPersistence {
    private final String baseDirectory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public SeriesPersistence(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        createDirectoryIfNotExists();
    }
    
    private void createDirectoryIfNotExists() {
        try {
            Files.createDirectories(Paths.get(baseDirectory));
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório: " + e.getMessage());
        }
    }
    
    /**
     * Guarda dados de um dia em disco
     */
    public void saveDayData(String username, int dia, DayData dayData) throws IOException {
        lock.writeLock().lock();
        try {
            String filename = buildFilename(username, dia);
            Path filepath = Paths.get(baseDirectory, filename);
            
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(filepath.toFile())))) {
                oos.writeObject(dayData);
            }
            
            System.out.println("Persistido: " + username + " dia " + dia);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Carrega dados de um dia do disco
     */
    public DayData loadDayData(String username, int dia) throws IOException, ClassNotFoundException {
        lock.readLock().lock();
        try {
            String filename = buildFilename(username, dia);
            Path filepath = Paths.get(baseDirectory, filename);
            
            if (!Files.exists(filepath)) {
                return null;
            }
            
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(new FileInputStream(filepath.toFile())))) {
                DayData dayData = (DayData) ois.readObject();
                System.out.println("Carregado: " + username + " dia " + dia);
                return dayData;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Verifica se dados de um dia existem em disco
     */
    public boolean exists(String username, int dia) {
        String filename = buildFilename(username, dia);
        Path filepath = Paths.get(baseDirectory, filename);
        return Files.exists(filepath);
    }
    
    /**
     * Remove dados de um dia do disco
     */
    public void deleteDayData(String username, int dia) throws IOException {
        lock.writeLock().lock();
        try {
            String filename = buildFilename(username, dia);
            Path filepath = Paths.get(baseDirectory, filename);
            Files.deleteIfExists(filepath);
            System.out.println("Removido: " + username + " dia " + dia);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove todos os dados de dias anteriores a um limite
     */
    public void deleteOldDays(String username, int diaMinimo) throws IOException {
        lock.writeLock().lock();
        try {
            Path dir = Paths.get(baseDirectory);
            if (!Files.exists(dir)) return;
            
            Files.list(dir)
                    .filter(path -> path.getFileName().toString().startsWith(username + "_day_"))
                    .forEach(path -> {
                        try {
                            String filename = path.getFileName().toString();
                            int dia = extractDayFromFilename(filename);
                            if (dia < diaMinimo) {
                                Files.delete(path);
                                System.out.println("Removido arquivo antigo: " + filename);
                            }
                        } catch (IOException e) {
                            System.err.println("Erro ao remover: " + e.getMessage());
                        }
                    });
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private String buildFilename(String username, int dia) {
        return username + "_day_" + dia + ".ser";
    }
    
    private int extractDayFromFilename(String filename) {
        String[] parts = filename.replace(".ser", "").split("_day_");
        if (parts.length == 2) {
            return Integer.parseInt(parts[1]);
        }
        return -1;
    }
}