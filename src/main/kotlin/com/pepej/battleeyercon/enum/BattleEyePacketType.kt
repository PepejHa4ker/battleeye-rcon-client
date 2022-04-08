package com.pepej.battleeyercon.enum

enum class BattleEyePacketType(val type: Byte) {

    Login(0x00),
    Command( 0x01),
    Acknowledge(0x02);

}