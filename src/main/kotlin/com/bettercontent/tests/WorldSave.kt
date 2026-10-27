package com.bettercontent.tests

import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/** Parse the entire saved NBT, not just a gzip/header prefix. No Minecraft installation needed. */
fun validateWorldSave(path: Path) {
    check(Files.isRegularFile(path) && Files.size(path) > 0) { "world save is missing: $path" }
    DataInputStream(GZIPInputStream(Files.newInputStream(path))).use { input ->
        check(input.readUnsignedByte() == 10) { "level.dat root must be a compound" }
        input.readUTF()
        var hasData = false
        var hasTime = false
        var hasName = false
        fun length(): Int = input.readInt().also { check(it >= 0) { "negative NBT length" } }
        fun skip(bytes: Long) { input.skipNBytes(bytes) }
        fun payload(type: Int, depth: Int, worldData: Boolean = false) {
            check(depth <= 512) { "NBT exceeds Minecraft's nesting limit" }
            when (type) {
                1 -> input.readByte()
                2 -> input.readShort()
                3 -> input.readInt()
                4 -> input.readLong()
                5 -> input.readFloat()
                6 -> input.readDouble()
                7 -> skip(length().toLong())
                8 -> input.readUTF()
                9 -> {
                    val element = input.readUnsignedByte()
                    val count = length()
                    check(element in 0..12 && (element != 0 || count == 0)) { "invalid NBT list type" }
                    repeat(count) { payload(element, depth + 1) }
                }
                10 -> {
                    while (true) {
                        val child = input.readUnsignedByte()
                        if (child == 0) break
                        val name = input.readUTF()
                        val data = depth == 0 && name == "Data" && child == 10
                        if (data) hasData = true
                        if (worldData && name == "Time" && child == 4) hasTime = true
                        if (worldData && name == "LevelName" && child == 8) hasName = true
                        payload(child, depth + 1, data)
                    }
                }
                11 -> skip(length().toLong() * 4)
                12 -> skip(length().toLong() * 8)
                else -> error("unknown NBT tag type $type")
            }
        }
        payload(10, 0)
        check(input.read() == -1) { "trailing bytes after level.dat root" }
        check(hasData && hasTime && hasName) { "level.dat lacks world data/time/name" }
    }
}
