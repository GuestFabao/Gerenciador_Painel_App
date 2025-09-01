package com.fabio.gerenciadoriptv

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsRef = db.collection("configuracoes").document("precos")

    private lateinit var monthlyPriceEditText: EditText
    private lateinit var quarterlyPriceEditText: EditText
    private lateinit var creditCostEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var changePasswordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Configurações"

        monthlyPriceEditText = findViewById(R.id.editTextMonthlyPrice)
        quarterlyPriceEditText = findViewById(R.id.editTextQuarterlyPrice)
        creditCostEditText = findViewById(R.id.editTextCreditCost)
        saveButton = findViewById(R.id.buttonSaveChanges)
        changePasswordButton = findViewById(R.id.buttonChangePassword)

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }

        changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val oldPasswordEditText = dialogView.findViewById<EditText>(R.id.editTextOldPassword)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.editTextNewPassword)
        val confirmPasswordEditText = dialogView.findViewById<EditText>(R.id.editTextConfirmPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("Alterar Senha")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val oldPass = oldPasswordEditText.text.toString()
                val newPass = newPasswordEditText.text.toString()
                val confirmPass = confirmPasswordEditText.text.toString()

                if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, "A nova senha deve ter no mínimo 6 caracteres.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, "As novas senhas não coincidem.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val user = auth.currentUser ?: return@setPositiveButton
                val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)

                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Toast.makeText(this, "Senha alterada com sucesso!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Erro ao alterar a senha.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Senha antiga incorreta.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            finish() // <-- ESTA LINHA FECHA A TELA E VOLTA PARA A PRINCIPAL
        }.addOnFailureListener {
            Toast.makeText(this, "Erro ao salvar.", Toast.LENGTH_SHORT).show()
        }
    }
}