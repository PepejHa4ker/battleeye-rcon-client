package com.pepej.battleeyercon.mapper

import com.pepej.battleeyercon.client.BattleEyeClient

interface MappableObject {

    val content: String
}

inline fun <reified T, R> MappableObject.mapTo(client: BattleEyeClient): R {
    val mapperClass = T::class.java
    val mapper = client.mappers[mapperClass] ?: throw IllegalStateException("No mapper for object found")
    return mapper.map(this.content) as R


}

