package com.fabio.gerenciadoriptv

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val settingsRef = db.collection("configuracoes").document("precos")

    private lateinit var monthlyPriceEditText: EditText
    private lateinit var quarterlyPriceEditText: EditText
    private lateinit var creditCostEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Configurações"

        monthlyPriceEditText = findViewById(R.id.editTextMonthlyPrice)
        quarterlyPriceEditText = findViewById(R.id.editTextQuarterlyPrice)
        creditCostEditText = findViewById(R.id.editTextCreditCost)
        saveButton = findViewById(R.id.buttonSaveChanges)

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadSettings() {
        settingsRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                monthlyPriceEditText.setText(document.getDouble("planoMensal")?.toString() ?: "")
                quarterlyPriceEditText.setText(document.getDouble("planoTrimestral")?.toString() ?: "")
                creditCostEditText.setText(document.getDouble("custoCredito")?.toString() ?: "")
            }
        }
    }

    private fun saveSettings() {
        val monthlyPrice = monthlyPriceEditText.text.toString().toDoubleOrNull() ?: 30.0
        val quarterlyPrice = quarterlyPriceEditText.text.toString().toDoubleOrNull() ?: 90.0
        val creditCost = creditCostEditText.text.toString().toDoubleOrNull() ?: 10.0

        val settings = hashMapOf(
            "planoMensal" to monthlyPrice,
            "planoTrimestral" to quarterlyPrice,
            "custoCredito" to creditCost
        )

        settingsRef.set(settings).addOnSuccessListener {
            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Erro ao salvar.", Toast.LENGTH_SHORT).show()
        }
    }
}