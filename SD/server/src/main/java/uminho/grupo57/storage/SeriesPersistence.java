package uminho.grupo57.storage;

import uminho.grupo57.entities.Event;
import uminho.grupo57.entities.TimeSeries;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Guarda dados de um evento em disco
     */
    public void saveEvento(Event evento, String nomeProduto, int dia) throws IOException
    {
        lock.writeLock().lock();
        try{
            Path dayDir = Paths.get(baseDirectory, String.valueOf(dia));
            Files.createDirectories(dayDir);

            int produtoHash = nomeProduto.hashCode();
            Path filePath = dayDir.resolve(String.valueOf(produtoHash));

            try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath.toFile(), true)))) {
                evento.writeEvento(out);
            }

        }finally{
            lock.writeLock().unlock();
        }
    }

    /**
     * Carrega dados de um dia do disco
     */
    public TimeSeries loadDayData(int dia) throws IOException
    {
        lock.readLock().lock();
        try{
            Path dayDir = Paths.get(baseDirectory, String.valueOf(dia));
            if(!Files.exists(dayDir))
                return null;

            Map<Integer, List<Event>> eventosPorProduto = new HashMap<>();

            File[] productFiles = dayDir.toFile().listFiles(File::isFile); //Listar todos os ficheiros de produtos e confirmar se são mesmo ficheiros
            if(productFiles == null)
                return new TimeSeries(dia);

            for(File file : productFiles)
            {
                int produtoHash = Integer.parseInt(file.getName());
                List<Event> eventos = new ArrayList<>();

                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
                {
                    while (true)
                    {
                        try{
                            eventos.add(Event.readEvento(in));
                        }catch (EOFException eof){
                            break;
                        }
                    }
                }
                if(!eventos.isEmpty())
                    eventosPorProduto.put(produtoHash, eventos);
            }
            return new TimeSeries(dia, eventosPorProduto);

        }finally{
            lock.readLock().unlock();
        }
    }


    /**
     * Verifica se dados de um dia existem em disco
     */
    public boolean exists(int dia)
    {
        lock.readLock().lock();
        try {
            Path filepath = Paths.get(baseDirectory, String.valueOf(dia));
            return Files.exists(filepath);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Guarda o dia atual num arquivo 'dia.dat'
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
        try{
            Path filepath = Paths.get(baseDirectory, "dia.dat");
            if(!Files.exists(filepath))
                return -1;

            File ficheiro = filepath.toFile();
            try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ficheiro))))
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

    public void deleteOldestDayIfLowerThanMax(int maxDias, int curDay)
    {
        lock.writeLock().lock();
        try{
            Path baseDir = Paths.get(baseDirectory);
            File[] dayDirs = baseDir.toFile().listFiles(File::isDirectory);

            if(dayDirs == null || dayDirs.length <= maxDias)
                return;

            int oldestDay = curDay;
            Path oldestDayPath = null;

            for(File dir : dayDirs)
            {
                try{
                    int dayNumber = Integer.parseInt(dir.getName());
                    if(dayNumber < oldestDay)
                    {
                        oldestDay = dayNumber;
                        oldestDayPath = dir.toPath();
                    }
                }catch (NumberFormatException e){continue;} //Se a diretoria não tiver um número como nome ignorar
            }

            if(oldestDayPath != null)
                deleteDirectory(oldestDayPath);

        }catch (IOException e){
            System.err.println("Erro ao apagar diretoria mais antiga: " + e.getMessage());
        }finally{
            lock.writeLock().unlock();
        }
    }

    /**
     * Método auxiliar para deletar uma diretoria e todos os seus ficheiros
     */
    private void deleteDirectory(Path directory) throws IOException
    {
        if(Files.exists(directory))
        {
            File dir = directory.toFile();
            File[] files = dir.listFiles();
            if(files != null)
            {
                for(File file : files)
                {
                    if(!file.delete())
                    {
                        System.err.println("Falha ao deletar arquivo: " + file.getAbsolutePath());
                    }
                }
            }
            Files.delete(directory);
        }
    }
}