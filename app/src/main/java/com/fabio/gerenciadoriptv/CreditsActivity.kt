package com.fabio.gerenciadoriptv

import android.os.Bundle
import android.view.View // <-- IMPORT QUE FALTAVA
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu // <-- IMPORT QUE FALTAVA
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Date

class CreditsActivity : AppCompatActivity() {
    private val db = Firebase.firestore
    private lateinit var adapter: CreditHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credits)

        val toolbar: Toolbar = findViewById(R.id.toolbar_credits)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewCreditHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val quantityEditText: EditText = findViewById(R.id.editTextQuantity)
        val addButton: Button = findViewById(R.id.buttonAddPurchase)

        addButton.setOnClickListener {
            val quantity = quantityEditText.text.toString().toLongOrNull()
            if (quantity != null && quantity > 0) {
                addCreditPurchase(quantity)
                quantityEditText.text.clear()
            } else {
                Toast.makeText(this, "Insira uma quantidade válida.", Toast.LENGTH_SHORT).show()
            }
        }

        listenForData()
    }

    private fun listenForData() {
        // Listener para o histórico
        db.collection("comprasCredito")
            .orderBy("data", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val historyList = snapshot.documents.map { doc ->
                        val data = doc.data!!
                        data["id"] = doc.id // Adiciona o ID do documento aos dados
                        data
                    }
                    adapter = CreditHistoryAdapter(historyList) { purchase, view ->
                        showPurchaseOptions(purchase, view)
                    }
                    findViewById<RecyclerView>(R.id.recyclerViewCreditHistory).adapter = adapter

                    // Calcula compras no mês
                    val purchasedThisMonth = historyList.filter {
                        val timestamp = it["data"] as com.google.firebase.Timestamp
                        val purchaseDate = timestamp.toDate()
                        val calendar = Calendar.getInstance()
                        val currentMonth = calendar.get(Calendar.MONTH)
                        calendar.time = purchaseDate
                        val purchaseMonth = calendar.get(Calendar.MONTH)
                        currentMonth == purchaseMonth
                    }.sumOf { (it["quantidade"] as Long) }
                    findViewById<TextView>(R.id.textViewPurchasedThisMonth).text = purchasedThisMonth.toString()
                }
            }

        // Listener para o saldo
        db.collection("contabilidade").document("saldoCreditos")
            .addSnapshotListener { snapshot, _ ->
                val saldo = snapshot?.getDouble("saldo")?.toInt() ?: 0
                findViewById<TextView>(R.id.textViewCreditBalance).text = saldo.toString()
            }
    }

    private fun showPurchaseOptions(purchase: Map<String, Any>, view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Excluir")
        // No futuro, podemos adicionar "Editar" aqui
        popup.setOnMenuItemClickListener {
            deletePurchase(purchase)
            true
        }
        popup.show()
    }

    private fun addCreditPurchase(quantity: Long) {
        val purchase = hashMapOf(
            "quantidade" to quantity,
            "data" to Date()
        )
        db.collection("comprasCredito").add(purchase)
            .addOnSuccessListener {
                db.collection("contabilidade").document("saldoCreditos")
                    .update("saldo", FieldValue.increment(quantity.toDouble()))
            }
    }

    private fun deletePurchase(purchase: Map<String, Any>) {
        val id = purchase["id"] as String
        val quantity = purchase["quantidade"] as Long

        db.collection("comprasCredito").document(id).delete()
            .addOnSuccessListener {
                db.collection("contabilidade").document("saldoCreditos")
                    .update("saldo", FieldValue.increment(-quantity.toDouble()))
                Toast.makeText(this, "Compra excluída.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}