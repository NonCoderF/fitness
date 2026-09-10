package io.motionguard.core

object PoseFrameSerializer {
    private const val Header = "timestampMs,leftHip,rightHip,leftKnee,rightKnee,leftAnkle,rightAnkle,leftShoulder,rightShoulder"

    fun serialize(frames: List<PoseFrame>): String = buildString {
        appendLine(Header)
        frames.forEach { frame ->
            append(frame.timestampMs)
            append(',')
            append(listOf(frame.leftHip, frame.rightHip, frame.leftKnee, frame.rightKnee, frame.leftAnkle, frame.rightAnkle, frame.leftShoulder, frame.rightShoulder).joinToString(",") { it.token() })
            appendLine()
        }
    }

    fun deserialize(text: String): List<PoseFrame> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("timestampMs") }
        .mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size < 9) return@mapNotNull null
            PoseFrame(
                timestampMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
                leftHip = parts[1].point(),
                rightHip = parts[2].point(),
                leftKnee = parts[3].point(),
                rightKnee = parts[4].point(),
                leftAnkle = parts[5].point(),
                rightAnkle = parts[6].point(),
                leftShoulder = parts[7].point(),
                rightShoulder = parts[8].point(),
            )
        }
        .toList()

    private fun Point2D?.token(): String = this?.let { "${it.x};${it.y};${it.confidence}" } ?: "-"

    private fun String.point(): Point2D? {
        if (this == "-") return null
        val values = split(';')
        if (values.size != 3) return null
        return Point2D(values[0].toFloat(), values[1].toFloat(), values[2].toFloat())
    }
}
