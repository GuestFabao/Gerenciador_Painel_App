package com.fabio.gerenciadoriptv

import com.google.firebase.firestore.DocumentId

data class Cliente(
    @DocumentId
    val id: String? = null,
    val nome: String = "",
    val obs: String = "", // <-- ALTERADO DE loginCliente PARA obs
    val plano: String = "",
    val valor: Double = 0.0,
    val vencimento: String = "",
    val status: String = ""
)