package com.pepej.battleeyercon.mapper

import com.pepej.battleeyercon.client.BattleEyeClient
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

interface ResponseMapper {

    val content: String
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified R> ResponseMapper.mapTo(client: BattleEyeClient): R? {
    val mapperClasses = client.getAllCommandMappers()
    val mapper =  mapperClasses.find { it.type == typeOf<R>().javaType } ?: throw IllegalStateException("No mapper for object found")
    return mapper.map(this.content) as R?

}

