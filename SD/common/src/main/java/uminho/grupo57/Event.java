package uminho.grupo57;

import java.io.Serializable;
import java.time.LocalTime;

public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String nomeProduto;
    private final LocalTime horaEvento;
    private final int quantidade;
    private final float preco;
    private final int dia; // Novo: dia em que o evento ocorreu

    public Event(String nomeProduto, int quantidade, float preco, int dia) {
        this.nomeProduto = nomeProduto;
        this.horaEvento = LocalTime.now();
        this.quantidade = quantidade;
        this.preco = preco;
        this.dia = dia;
    }
    
    // Construtor para manter compatibilidade (assume dia 0)
    public Event(String nomeProduto, int quantidade, float preco) {
        this(nomeProduto, quantidade, preco, 0);
    }

    public Event(Event original) {
        this.nomeProduto = original.nomeProduto;
        this.horaEvento = original.horaEvento;
        this.quantidade = original.quantidade;
        this.preco = original.preco;
        this.dia = original.dia;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dia ").append(dia).append(" - ");
        sb.append(this.horaEvento.toString());
        sb.append("\n\tQuantidade: ");
        sb.append(quantidade);
        sb.append(" unidades\n\tValor da Compra:");
        sb.append(preco);
        sb.append("€");
        return sb.toString();
    }

    public boolean equals(Object o) {
        if(o.getClass() != this.getClass()) return false;

        Event comp = (Event)o;
        return comp.preco == this.preco && comp.quantidade == this.quantidade
                && this.nomeProduto.equals(comp.nomeProduto) 
                && this.horaEvento.equals(comp.horaEvento)
                && this.dia == comp.dia;
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
    
    public LocalTime getHoraEvento() {
        return this.horaEvento;
    }
    
    public int getDia() {
        return this.dia;
    }
}