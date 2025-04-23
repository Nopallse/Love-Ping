package com.example.loveping

import java.util.Date

data class PairRequest(
    val id: String,
    val sender: dataUser,
    val timestamp: Date
)