package com.pepej.battleeyercon.models

interface MappableEntity<T> {

    fun map(tokens: List<String>): T?

}