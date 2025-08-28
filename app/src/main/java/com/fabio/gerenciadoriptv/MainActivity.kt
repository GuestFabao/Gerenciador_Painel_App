package com.fabio.gerenciadoriptv

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var clientesAdapter: ClientesAdapter
    private val db = Firebase.firestore
    private val clientesCollectionRef = db.collection("clientes")
    private val contabilidadeRef = db.collection("contabilidade")
    private lateinit var auth: FirebaseAuth

    private lateinit var textViewTotalClients: TextView
    private lateinit var textViewTotalReceived: TextView
    private lateinit var textViewTotalPending: TextView
    private lateinit var textViewCredits: TextView
    private lateinit var textViewProfit: TextView
    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewEmptyList: TextView

    private var fullClientList = listOf<Cliente>()
    private var creditCost = 10.0
    private var monthlyPrice = 30.0
    private var quarterlyPrice = 90.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        textViewTotalClients = findViewById(R.id.textViewTotalClients)
        textViewTotalReceived = findViewById(R.id.textViewTotalReceived)
        textViewTotalPending = findViewById(R.id.textViewTotalPending)
        textViewCredits = findViewById(R.id.textViewCredits)
        textViewProfit = findViewById(R.id.textViewProfit)
        searchEditText = findViewById(R.id.searchEditText)
        recyclerView = findViewById(R.id.recyclerViewClients)
        progressBar = findViewById(R.id.progressBar)
        textViewEmptyList = findViewById(R.id.textViewEmptyList)
        val fab: FloatingActionButton = findViewById(R.id.fabAddClient)

        recyclerView.layoutManager = LinearLayoutManager(this)
        clientesAdapter = ClientesAdapter { cliente -> showClientOptionsDialog(cliente) }
        recyclerView.adapter = clientesAdapter

        fetchData()

        fab.setOnClickListener { showAddClientDialog() }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                Toast.makeText(this, "Logout efetuado.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.action_add_credits -> {
                // ALTERAÇÃO AQUI: Abre a nova tela de Gestão de Créditos
                startActivity(Intent(this, CreditsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchData() {
        listenForCreditBalance()
        loadSettingsAndThenClients()
    }

    private fun listenForCreditBalance() {
        contabilidadeRef.document("saldoCreditos").addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w("Firebase", "Erro ao buscar saldo de créditos.", error); return@addSnapshotListener }
            val saldo = snapshot?.getDouble("saldo")?.toInt() ?: 0
            textViewCredits.text = saldo.toString()
        }
    }

    private fun loadSettingsAndThenClients() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        textViewEmptyList.visibility = View.GONE

        db.collection("configuracoes").document("precos").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    creditCost = document.getDouble("custoCredito") ?: 10.0
                    monthlyPrice = document.getDouble("planoMensal") ?: 30.0
                    quarterlyPrice = document.getDouble("planoTrimestral") ?: 90.0
                }
                listenForClients()
            }
            .addOnFailureListener {
                Log.w("Firebase", "Erro ao buscar configurações. Usando valores padrão.", it)
                listenForClients()
            }
    }

    private fun listenForClients() {
        clientesCollectionRef.orderBy("nome").addSnapshotListener { snapshot, error ->
            progressBar.visibility = View.GONE
            if (error != null) {
                Log.w("Firebase", "Erro ao buscar clientes.", error)
                textViewEmptyList.text = "Erro ao carregar dados."
                textViewEmptyList.visibility = View.VISIBLE
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val clientes = snapshot.toObjects(Cliente::class.java)
                fullClientList = clientes

                if (clientes.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    textViewEmptyList.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    textViewEmptyList.visibility = View.GONE
                }
                filter(searchEditText.text.toString())
                updateDashboard(clientes)
            }
        }
    }

    private fun filter(text: String) {
        val filteredList = mutableListOf<Cliente>()
        if (text.isEmpty()) {
            filteredList.addAll(fullClientList)
        } else {
            for (cliente in fullClientList) {
                if (cliente.nome.lowercase(Locale.getDefault()).contains(text.lowercase(Locale.getDefault()))) {
                    filteredList.add(cliente)
                }
            }
        }
        clientesAdapter.submitList(filteredList)
    }

    private fun updateDashboard(clientes: List<Cliente>) {
        val totalClients = clientes.size
        var totalReceived = 0.0
        var totalPending = 0.0
        var clientesPagosCount = 0

        val hoje = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (cliente in clientes) {
            val dataVencimento = try { sdf.parse(cliente.vencimento) } catch (e: Exception) { null }
            var statusFinal = cliente.status
            if (dataVencimento != null && dataVencimento.before(hoje.time) && cliente.status != "Pago") {
                statusFinal = "Atrasado"
            }
            if (statusFinal == "Pago") {
                totalReceived += cliente.valor
                clientesPagosCount++
            } else {
                totalPending += cliente.valor
            }
        }

        val custoTotal = clientesPagosCount * creditCost
        val lucro = totalReceived - custoTotal

        textViewTotalClients.text = totalClients.toString()
        textViewTotalReceived.text = String.format(Locale.getDefault(), "R$ %.2f", totalReceived)
        textViewTotalPending.text = String.format(Locale.getDefault(), "R$ %.2f", totalPending)
        textViewProfit.text = String.format(Locale.getDefault(), "R$ %.2f", lucro)
    }

    private fun showClientOptionsDialog(cliente: Cliente) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_options, null)
        dialog.setContentView(view)

        val optionConfirm = view.findViewById<TextView>(R.id.option_confirm_payment)
        val optionEdit = view.findViewById<TextView>(R.id.option_edit)
        val optionDelete = view.findViewById<TextView>(R.id.option_delete)
        val optionMarkUnpaid = view.findViewById<TextView>(R.id.option_mark_unpaid)

        val redColor = ContextCompat.getColor(this, R.color.colorAtrasado)
        optionDelete.setTextColor(redColor)
        optionDelete.compoundDrawablesRelative[0]?.setTint(redColor)

        if (cliente.status == "Pago") {
            optionMarkUnpaid.visibility = View.VISIBLE
            optionConfirm.visibility = View.GONE
        } else {
            optionMarkUnpaid.visibility = View.GONE
            optionConfirm.visibility = View.VISIBLE
        }

        optionConfirm.setOnClickListener { confirmPaymentAndUpdateDueDate(cliente); dialog.dismiss() }
        optionEdit.setOnClickListener { showEditClientDialog(cliente); dialog.dismiss() }
        optionDelete.setOnClickListener { deleteClient(cliente); dialog.dismiss() }
        optionMarkUnpaid.setOnClickListener { markAsUnpaid(cliente); dialog.dismiss() }

        dialog.show()
    }

    private fun markAsUnpaid(cliente: Cliente) {
        cliente.id?.let { clientId ->
            clientesCollectionRef.document(clientId)
                .update("status", "Pendente")
                .addOnSuccessListener {
                    Toast.makeText(this, "${cliente.nome} marcado como Pendente.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupPlanAndValueLogic(dialogView: View, cliente: Cliente? = null) {
        val planAutoComplete: AutoCompleteTextView = dialogView.findViewById(R.id.autoCompletePlan)
        val valueEditText = dialogView.findViewById<EditText>(R.id.editTextValue)

        val plans = arrayOf("Mensal", "Trimestral")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, plans)
        planAutoComplete.setAdapter(adapter)

        cliente?.let {
            planAutoComplete.setText(it.plano, false)
        }

        planAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedPlan = plans[position]
            if (selectedPlan == "Mensal") {
                valueEditText.setText(monthlyPrice.toString())
            } else if (selectedPlan == "Trimestral") {
                valueEditText.setText(quarterlyPrice.toString())
            }
        }
    }

    private fun showAddClientDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_client, null)
        val dueDateEditText = dialogView.findViewById<EditText>(R.id.editTextDueDate)
        setupPlanAndValueLogic(dialogView)

        dueDateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val formattedDate = String.format("%d-%02d-%02d", year, month + 1, day)
                dueDateEditText.setText(formattedDate)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Adicionar Novo Cliente")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.editTextClientName).text.toString()
                val plan = dialogView.findViewById<AutoCompleteTextView>(R.id.autoCompletePlan).text.toString()
                val value = dialogView.findViewById<EditText>(R.id.editTextValue).text.toString().toDoubleOrNull() ?: 0.0
                val dueDate = dueDateEditText.text.toString()
                if (name.isNotBlank() && dueDate.isNotBlank()) {
                    val newClient = Cliente(nome = name, plano = plan, valor = value, vencimento = dueDate, status = "Pendente")
                    clientesCollectionRef.add(newClient)
                        .addOnSuccessListener { Toast.makeText(this, "$name adicionado.", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, "Nome e Data são obrigatórios.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditClientDialog(cliente: Cliente) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_client, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextClientName)
        val valueEditText = dialogView.findViewById<EditText>(R.id.editTextValue)
        val dueDateEditText = dialogView.findViewById<EditText>(R.id.editTextDueDate)

        nameEditText.setText(cliente.nome)
        valueEditText.setText(cliente.valor.toString())
        dueDateEditText.setText(cliente.vencimento)

        setupPlanAndValueLogic(dialogView, cliente)

        dueDateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val formattedDate = String.format("%d-%02d-%02d", y, m + 1, d)
                dueDateEditText.setText(formattedDate)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Cliente")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = nameEditText.text.toString()
                val newPlan = dialogView.findViewById<AutoCompleteTextView>(R.id.autoCompletePlan).text.toString()
                val newValue = valueEditText.text.toString().toDoubleOrNull() ?: 0.0
                val newDueDate = dueDateEditText.text.toString()
                if (newName.isNotBlank() && newDueDate.isNotBlank()) {
                    val updatedData = mapOf("nome" to newName, "plano" to newPlan, "valor" to newValue, "vencimento" to newDueDate)
                    cliente.id?.let {
                        clientesCollectionRef.document(it).update(updatedData)
                            .addOnSuccessListener { Toast.makeText(this, "Dados atualizados.", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmPaymentAndUpdateDueDate(cliente: Cliente) {
        val saldoCreditosRef = contabilidadeRef.document("saldoCreditos")
        db.runTransaction { transaction ->
            val saldoDoc = transaction.get(saldoCreditosRef)
            val saldoAtual = saldoDoc.getDouble("saldo")?.toInt() ?: 0
            if (saldoAtual <= 0) {
                throw Exception("Saldo de créditos insuficiente!")
            }
            transaction.update(saldoCreditosRef, "saldo", FieldValue.increment(-1.0))

            val calendar = Calendar.getInstance()
            when (cliente.plano.lowercase(Locale.ROOT).trim()) {
                "mensal" -> calendar.add(Calendar.MONTH, 1)
                "trimestral" -> calendar.add(Calendar.MONTH, 3)
                else -> throw Exception("Plano '${cliente.plano}' não reconhecido.")
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val novaDataVencimento = dateFormat.format(calendar.time)

            val clienteRef = clientesCollectionRef.document(cliente.id!!)
            transaction.update(clienteRef, mapOf("status" to "Pago", "vencimento" to novaDataVencimento))
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "${cliente.nome} renovado! 1 crédito utilizado.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Falha na renovação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteClient(cliente: Cliente) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Cliente")
            .setMessage("Tem certeza que deseja excluir ${cliente.nome} permanentemente?")
            .setPositiveButton("Excluir") { _, _ ->
                cliente.id?.let { clientId ->
                    clientesCollectionRef.document(clientId).delete()
                        .addOnSuccessListener { Toast.makeText(this, "${cliente.nome} excluído.", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}