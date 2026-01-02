package uminho.grupo57.entities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Representa um evento de compra num dia específico.
 * <p>Imutável: contém produto, quantidade, preço e dia.</p>
 */
public class Event
{
    private final int dia;
    private final String nomeProduto;
    private final int quantidade;
    private final float preco;

    /**
     * Constrói um novo evento.
     *
     * @param nomeProduto Nome do produto
     * @param quantidade Quantidade comprada
     * @param preco Preço unitário
     * @param dia Dia em que o evento ocorreu
     */
    public Event(String nomeProduto, int quantidade, float preco, int dia)
    {
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.preco = preco;
        this.dia = dia;
    }

    /**
     * Construtor de cópia.
     *
     * @param original Evento a copiar
     */
    public Event(Event original)
    {
        this.nomeProduto = original.nomeProduto;
        this.quantidade = original.quantidade;
        this.preco = original.preco;
        this.dia = original.dia;
    }

    @Override
    public boolean equals(Object o)
    {
        if(o.getClass() != this.getClass())
            return false;

        Event comp = (Event)o;
        return comp.preco == this.preco && comp.quantidade == this.quantidade
                && this.nomeProduto.equals(comp.nomeProduto);
    }

    /**
     * Retorna uma cópia do evento.
     *
     * @return nova instância idêntica
     */
    public Event clone() {
        return new Event(this);
    }

    /** @return nome do produto */
    public String getNome() {
        return this.nomeProduto;
    }
    
    /** @return quantidade comprada */
    public int getQuantidade() {
        return this.quantidade;
    }
    
    /** @return preço unitário */
    public float getPreco() {
        return this.preco;
    }

    /** @return dia do evento */
    public int getDia() {return this.dia;}

    /**
     * Serializa o evento para stream binário.
     *
     * @param out Stream de saída
     * @throws IOException Se ocorrer erro de I/O
     */
    public void writeEvento(DataOutputStream out) throws IOException
    {
        out.writeUTF(nomeProduto);
        out.writeInt(quantidade);
        out.writeFloat(preco);
        out.writeInt(dia);
    }

    /**
     * Deserializa um evento de stream binário.
     *
     * @param in Stream de entrada
     * @return Evento lido
     * @throws IOException Se ocorrer erro de I/O
     */
    public static Event readEvento(DataInputStream in) throws IOException
    {
        String nomeProduto = in.readUTF();
        int quantidade = in.readInt();
        float preco = in.readFloat();
        int dia = in.readInt();

        return new Event(nomeProduto, quantidade, preco, dia);
    }
}