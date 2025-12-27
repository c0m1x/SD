package uminho.grupo57.storage;

import uminho.grupo57.entities.TimeSeries;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestão de persistência de séries em disco
 * Guarda e carrega TimeSeries de/para disco
 */
public class SeriesPersistence {

    private final String baseDirectory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SeriesPersistence(String baseDirectory)
    {
        this.baseDirectory = baseDirectory;
        createDirectoryIfNotExists();
    }

    private void createDirectoryIfNotExists()
    {
        try{
            Files.createDirectories(Paths.get(baseDirectory));
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório base: " + e.getMessage());
        }
    }

    /**
     * Guarda dados de um dia em disco
     */
    public void saveDayData(String username, int dia, TimeSeries dayData) throws IOException
    {
        lock.writeLock().lock();
        try{
            Path filepath = Paths.get(baseDirectory, buildFilename(username, dia));
            Files.createDirectories(filepath.getParent());

            try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filepath.toFile()))))
            {
                dayData.writeTimeSeries(out);
            }

            System.out.println("Persistido: " + username + " dia " + dia);
        }finally{
            lock.writeLock().unlock();
        }
    }

    /**
     * Carrega dados de um dia do disco
     */
    public TimeSeries loadDayData(String username, int dia) throws IOException
    {
        lock.readLock().lock();
        try{
            Path filepath = Paths.get(baseDirectory, buildFilename(username, dia));

            if(!Files.exists(filepath))
                return null;

            try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filepath.toFile()))))
            {
                TimeSeries dayData = TimeSeries.readTimeSeries(in);
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
    public boolean exists(String username, int dia)
    {
        lock.readLock().lock();
        try {
            Path filepath = Paths.get(baseDirectory, buildFilename(username, dia));
            return Files.exists(filepath);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String buildFilename(String username, int dia)
    {
        return username + "_day_" + dia + ".ser";
    }

    /**
     * Guarda o dia atual em um arquivo 'dia.dat'
     */
    public void saveCurrentDay(int currentDay)
    {
        lock.writeLock().lock();
        try{
            Path filepath = Paths.get(baseDirectory, "dia.dat");
            Files.createDirectories(filepath.getParent());
            try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filepath.toFile()))))
            {
                out.writeInt(currentDay);
                out.flush();
            }catch (IOException e){
                System.err.println("Erro ao salvar dia atual: " + e.getMessage());
            }
        }catch (IOException e){
            System.err.println("Erro ao criar diretório para dia.dat: " + e.getMessage());
        }finally{
            lock.writeLock().unlock();
        }
    }

    /**
     * Lê o dia atual do arquivo 'dia.dat'
     * @return dia atual ou -1 se não existir ou ocorrer erro
     */
    public int getSavedDay()
    {
        lock.readLock().lock();
        try {
            Path filepath = Paths.get(baseDirectory, "dia.dat");
            if (!Files.exists(filepath))
                return -1;

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filepath.toFile()))))
            {
                return in.readInt();
            }catch (IOException e){
                System.err.println("Erro ao ler dia atual: " + e.getMessage());
                return -1;
            }
        }finally{
            lock.readLock().unlock();
        }
    }
}