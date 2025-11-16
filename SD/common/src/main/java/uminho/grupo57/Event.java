package uminho.grupo57;

import java.time.LocalTime;

public class Event
{
    private final String nomeProduto;
    private final LocalTime horaEvento;
    private final int quantidade;
    private final float preco;

    public Event(String nomeProduto, int quantidade, float preco)
    {
        this.nomeProduto = nomeProduto;
        this.horaEvento = LocalTime.now();
        this.quantidade = quantidade;
        this.preco = preco;
    }

    public Event(Event original)
    {
        this.nomeProduto = original.nomeProduto;
        this.horaEvento = original.horaEvento;
        this.quantidade = original.quantidade;
        this.preco = original.preco;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(this.horaEvento.toString());
        sb.append("\n\tQuantidade: ");
        sb.append(quantidade);
        sb.append(" unidades\n\tValor da Compra:");
        sb.append(preco);
        sb.append("€");
        return sb.toString();
    }

    public boolean equals(Object o)
    {
        if(o.getClass() != this.getClass()) return false;

        Event comp = (Event)o;
        return comp.preco == this.preco && comp.quantidade == this.quantidade
                && this.nomeProduto.equals(comp.nomeProduto) && this.horaEvento.equals(comp.horaEvento);
    }

    public Event clone()
    {
        return new Event(this);
    }

    public String getNome()
    {
        return this.nomeProduto;
    }
    public int getQuantidade()
    {
        return this.quantidade;
    }
    public float getPreco()
    {
        return this.preco;
    }
    public LocalTime getHoraEvento()
    {
        return this.horaEvento;
    }
}