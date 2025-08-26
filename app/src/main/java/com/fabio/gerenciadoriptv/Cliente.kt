package com.fabio.gerenciadoriptv

import com.google.firebase.firestore.DocumentId

data class Cliente(
    @DocumentId // Anotação para pegar o ID do documento automaticamente
    val id: String? = null,
    val nome: String = "",
    val plano: String = "",
    val valor: Double = 0.0,
    val vencimento: String = "", // Formato "AAAA-MM-DD"
    val status: String = "" // "Pago", "Pendente", "Atrasado"
)