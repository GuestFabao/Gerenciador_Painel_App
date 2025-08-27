package com.fabio.gerenciadoriptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
                showAddCreditsDialog()
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
                }
                listenForClients()
            }
            .addOnFailureListener {
                Log.w("Firebase", "Erro ao buscar configurações. Usando custo padrão.", it)
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
        val options = arrayOf("Confirmar Pagamento e Renovar", "Editar Dados", "Excluir Cliente")
        AlertDialog.Builder(this)
            .setTitle(cliente.nome)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmPaymentAndUpdateDueDate(cliente)
                    1 -> showEditClientDialog(cliente)
                    2 -> deleteClient(cliente)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddCreditsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_credits, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.editTextCreditsAmount)
        AlertDialog.Builder(this)
            .setTitle("Adicionar Créditos")
            .setView(dialogView)
            .setPositiveButton("Adicionar") { _, _ ->
                val amount = amountEditText.text.toString().toLongOrNull()
                if (amount != null && amount > 0) {
                    val saldoCreditosRef = contabilidadeRef.document("saldoCreditos")
                    saldoCreditosRef.update("saldo", FieldValue.increment(amount.toDouble()))
                        .addOnSuccessListener {
                            Toast.makeText(this, "$amount créditos adicionados!", Toast.LENGTH_SHORT).show()
                            db.collection("comprasCredito").add(mapOf("quantidade" to amount, "data" to Calendar.getInstance().time))
                        }
                        .addOnFailureListener {
                            saldoCreditosRef.set(mapOf("saldo" to amount))
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

    private fun showEditClientDialog(cliente: Cliente) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_client, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextClientName)
        val planEditText = dialogView.findViewById<EditText>(R.id.editTextPlan)
        val valueEditText = dialogView.findViewById<EditText>(R.id.editTextValue)
        val dueDateEditText = dialogView.findViewById<EditText>(R.id.editTextDueDate)
        nameEditText.setText(cliente.nome)
        planEditText.setText(cliente.plano)
        valueEditText.setText(cliente.valor.toString())
        dueDateEditText.setText(cliente.vencimento)
        AlertDialog.Builder(this)
            .setTitle("Editar Cliente")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = nameEditText.text.toString()
                val newPlan = planEditText.text.toString()
                val newValue = valueEditText.text.toString().toDoubleOrNull() ?: 0.0
                val newDueDate = dueDateEditText.text.toString()
                if (newName.isNotBlank()) {
                    val updatedData = mapOf("nome" to newName, "plano" to newPlan, "valor" to newValue, "vencimento" to newDueDate)
                    cliente.id?.let {
                        clientesCollectionRef.document(it).update(updatedData)
                            .addOnSuccessListener { Toast.makeText(this, "Dados de ${cliente.nome} atualizados.", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

    private fun showAddClientDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_client, null)
        AlertDialog.Builder(this)
            .setTitle("Adicionar Novo Cliente")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.editTextClientName).text.toString()
                val plan = dialogView.findViewById<EditText>(R.id.editTextPlan).text.toString()
                val value = dialogView.findViewById<EditText>(R.id.editTextValue).text.toString().toDoubleOrNull() ?: 0.0
                val dueDate = dialogView.findViewById<EditText>(R.id.editTextDueDate).text.toString()
                if (name.isNotBlank()) {
                    val newClient = Cliente(nome = name, plano = plan, valor = value, vencimento = dueDate, status = "Pendente")
                    clientesCollectionRef.add(newClient)
                        .addOnSuccessListener { Toast.makeText(this, "$name adicionado com sucesso.", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}