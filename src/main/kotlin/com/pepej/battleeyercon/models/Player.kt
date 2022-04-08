package com.pepej.battleeyercon.models

data class Player(
    val number: Int,
    val ip: String,
    val ping: String,
    val guid: String,
    val name: String,
    val status: String,
    val lastSeen: String? = null,
    val lastSeenOn: String? = null,
    val location: String? = null,
    val comment: String? = null,
)
