package com.pepej.battleeyercon.utils

fun <E> MutableCollection<E>.addNotNull(element: E?) {
    if (element != null) this.add(element)
}