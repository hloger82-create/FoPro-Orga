package de.naturfoto.app.map

actual fun compressPhoto(bytes: ByteArray, maxSide: Int, quality: Int): ByteArray {
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
    val out = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    } else bmp
    val bos = java.io.ByteArrayOutputStream()
    out.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, bos)
    return bos.toByteArray()
}
