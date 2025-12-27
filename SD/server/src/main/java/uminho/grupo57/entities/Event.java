package uminho.grupo57.entities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalTime;

public class Event
{
    private final int dia;
    private final String nomeProduto;
    private final int quantidade;
    private final float preco;

    public Event(String nomeProduto, int quantidade, float preco, int dia)
    {
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.preco = preco;
        this.dia = dia;
    }

    public Event(Event original)
    {
        this.nomeProduto = original.nomeProduto;
        this.quantidade = original.quantidade;
        this.preco = original.preco;
        this.dia = original.dia;
    }

    public boolean equals(Object o)
    {
        if(o.getClass() != this.getClass())
            return false;

        Event comp = (Event)o;
        return comp.preco == this.preco && comp.quantidade == this.quantidade
                && this.nomeProduto.equals(comp.nomeProduto);
    }

    public Event clone() {
        return new Event(this);
    }

    public String getNome() {
        return this.nomeProduto;
    }
    
    public int getQuantidade() {
        return this.quantidade;
    }
    
    public float getPreco() {
        return this.preco;
    }

    public int getDia() {return this.dia;}

    public void writeEvento(DataOutputStream out) throws IOException
    {
        out.writeUTF(nomeProduto);
        out.writeInt(quantidade);
        out.writeFloat(preco);
        out.writeInt(dia);
    }

    public static Event readEvento(DataInputStream in) throws IOException
    {
        String nomeProduto = in.readUTF();
        int quantidade = in.readInt();
        float preco = in.readFloat();
        int dia = in.readInt();

        return new Event(nomeProduto, quantidade, preco, dia);
    }
}