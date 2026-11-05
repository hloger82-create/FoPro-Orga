package de.naturfoto.app.map

expect fun compressPhoto(bytes: ByteArray, maxSide: Int = 1920, quality: Int = 80): ByteArray
