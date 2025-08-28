package com.fabio.gerenciadoriptv

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
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
import java.util.Locale

class CreditsActivity : AppCompatActivity() {
    private val db = Firebase.firestore
    private var fullHistoryList = listOf<Map<String, Any>>()
    private lateinit var adapter: CreditHistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var textViewCreditBalance: TextView
    private lateinit var textViewPurchasedThisMonth: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credits)

        val toolbar: Toolbar = findViewById(R.id.toolbar_credits)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewCreditHistory)
        textViewCreditBalance = findViewById(R.id.textViewCreditBalance)
        textViewPurchasedThisMonth = findViewById(R.id.textViewPurchasedThisMonth)
        val quantityEditText: EditText = findViewById(R.id.editTextQuantity)
        val addButton: Button = findViewById(R.id.buttonAddPurchase)
        val dateFilterTextView: TextView = findViewById(R.id.textViewDateFilter)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CreditHistoryAdapter { purchase, view ->
            showPurchaseOptions(purchase, view)
        }
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            val quantity = quantityEditText.text.toString().toLongOrNull()
            if (quantity != null && quantity > 0) {
                addCreditPurchase(quantity)
                quantityEditText.text.clear()
            } else {
                Toast.makeText(this, "Insira uma quantidade válida.", Toast.LENGTH_SHORT).show()
            }
        }

        dateFilterTextView.setOnClickListener {
            showMonthYearPicker()
        }

        listenForData()
    }

    private fun showMonthYearPicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, _ ->
            filterHistoryByMonth(year, month)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun filterHistoryByMonth(year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        val filteredList = fullHistoryList.filter {
            val timestamp = it["data"] as com.google.firebase.Timestamp
            calendar.time = timestamp.toDate()
            calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month
        }
        adapter.updateList(filteredList)
    }

    private fun listenForData() {
        db.collection("comprasCredito")
            .orderBy("data", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val historyList = snapshot.documents.map { doc ->
                        val data = doc.data!!
                        data["id"] = doc.id
                        data
                    }
                    fullHistoryList = historyList
                    adapter.updateList(fullHistoryList)

                    val calendar = Calendar.getInstance()
                    val currentMonth = calendar.get(Calendar.MONTH)
                    val currentYear = calendar.get(Calendar.YEAR)

                    val purchasedThisMonth = historyList.filter {
                        val timestamp = it["data"] as com.google.firebase.Timestamp
                        calendar.time = timestamp.toDate()
                        calendar.get(Calendar.YEAR) == currentYear && calendar.get(Calendar.MONTH) == currentMonth
                    }.sumOf { (it["quantidade"] as Long) }
                    textViewPurchasedThisMonth.text = purchasedThisMonth.toString()
                }
            }

        db.collection("contabilidade").document("saldoCreditos")
            .addSnapshotListener { snapshot, _ ->
                val saldo = snapshot?.getDouble("saldo")?.toInt() ?: 0
                textViewCreditBalance.text = saldo.toString()
            }
    }

    private fun showPurchaseOptions(purchase: Map<String, Any>, view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Editar")
        popup.menu.add("Excluir")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                "Editar" -> showEditPurchaseDialog(purchase)
                "Excluir" -> deletePurchase(purchase)
            }
            true
        }
        popup.show()
    }

    private fun showEditPurchaseDialog(purchase: Map<String, Any>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_credits, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.editTextCreditsAmount)

        val oldQuantity = purchase["quantidade"] as Long
        amountEditText.setText(oldQuantity.toString())

        AlertDialog.Builder(this)
            .setTitle("Editar Compra de Créditos")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newQuantity = amountEditText.text.toString().toLongOrNull()
                if (newQuantity != null && newQuantity >= 0) {
                    val id = purchase["id"] as String
                    val difference = newQuantity - oldQuantity
                    db.collection("comprasCredito").document(id)
                        .update("quantidade", newQuantity)
                        .addOnSuccessListener {
                            db.collection("contabilidade").document("saldoCreditos")
                                .update("saldo", FieldValue.increment(difference.toDouble()))
                            Toast.makeText(this, "Compra atualizada.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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