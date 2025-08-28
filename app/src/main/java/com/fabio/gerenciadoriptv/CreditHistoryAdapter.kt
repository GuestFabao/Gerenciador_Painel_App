package com.fabio.gerenciadoriptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class CreditHistoryAdapter(
    private val onOptionClicked: (Map<String, Any>, View) -> Unit
) : RecyclerView.Adapter<CreditHistoryAdapter.ViewHolder>() {

    private var history = listOf<Map<String, Any>>()

    fun updateList(newList: List<Map<String, Any>>) {
        history = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateTextView: TextView = view.findViewById(R.id.textViewPurchaseDate)
        val quantityTextView: TextView = view.findViewById(R.id.textViewPurchaseQuantity)
        val optionsButton: ImageButton = view.findViewById(R.id.buttonOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_credit_purchase, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val purchase = history[position]
        val quantity = purchase["quantidade"] as? Long
        val timestamp = purchase["data"] as? Timestamp

        if (timestamp != null) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            holder.dateTextView.text = sdf.format(timestamp.toDate())
        }

        holder.quantityTextView.text = "+${quantity ?: 0} Créditos"

        holder.optionsButton.setOnClickListener {
            onOptionClicked(purchase, it)
        }
    }

    override fun getItemCount() = history.size
}