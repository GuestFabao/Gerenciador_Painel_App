package com.fabio.gerenciadoriptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ClientesAdapter(private val onClientClicked: (Cliente) -> Unit) : RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder>() {

    private var clientes = listOf<Cliente>()

    fun submitList(listaDeClientes: List<Cliente>) {
        clientes = listaDeClientes
        notifyDataSetChanged()
    }

    class ClienteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nomeTextView: TextView = itemView.findViewById(R.id.textViewClientName)
        val obsTextView: TextView = itemView.findViewById(R.id.textViewObs)
        val vencimentoTextView: TextView = itemView.findViewById(R.id.textViewDueDate)
        val statusTextView: TextView = itemView.findViewById(R.id.textViewStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cliente, parent, false)
        return ClienteViewHolder(view)
    }

    override fun getItemCount(): Int {
        return clientes.size
    }

    private fun formatDateToBrazilian(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = clientes[position]
        holder.nomeTextView.text = cliente.nome

        // Lógica para mostrar ou esconder o campo de observação
        if (cliente.obs.isNotBlank()) {
            holder.obsTextView.visibility = View.VISIBLE
            holder.obsTextView.text = cliente.obs
        } else {
            holder.obsTextView.visibility = View.GONE
        }

        holder.vencimentoTextView.text = "Vence em: ${formatDateToBrazilian(cliente.vencimento)}"

        holder.itemView.setOnClickListener {
            cliente.id?.let {
                onClientClicked(cliente)
            }
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val hoje = Calendar.getInstance()
        hoje.set(Calendar.HOUR_OF_DAY, 0); hoje.set(Calendar.MINUTE, 0); hoje.set(Calendar.SECOND, 0); hoje.set(Calendar.MILLISECOND, 0)
        val dataVencimento = try { sdf.parse(cliente.vencimento) } catch (e: Exception) { null }

        var statusFinal = cliente.status
        if (dataVencimento != null && dataVencimento.before(hoje.time) && cliente.status != "Pago") {
            statusFinal = "Atrasado"
        }

        holder.statusTextView.text = statusFinal

        val context = holder.itemView.context
        when (statusFinal) {
            "Pago" -> {
                holder.statusTextView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.colorPago)
                holder.statusTextView.setTextColor(ContextCompat.getColor(context, R.color.white))
            }
            "Pendente" -> {
                holder.statusTextView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.colorPendente)
                holder.statusTextView.setTextColor(ContextCompat.getColor(context, R.color.white))
            }
            "Atrasado" -> {
                holder.statusTextView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.colorAtrasado)
                holder.statusTextView.setTextColor(ContextCompat.getColor(context, R.color.white))
            }
        }
    }
}