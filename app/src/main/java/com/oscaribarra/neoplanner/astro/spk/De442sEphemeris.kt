package com.oscaribarra.neoplanner.astro.spk

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/**
 * Ephemeris backed by a DE442s SPK file.
 *
 * Uses:
 *  - SpkDafReader (Module 5) to get segment summaries
 *  - SpkType2Evaluator (Module 6) to evaluate type=2 Chebyshev position segments
 */
class De442sEphemeris(file: File) : Ephemeris {

    private val raf = RandomAccessFile(file, "r")

    private val index: SpkKernelIndex
    private val order: ByteOrder
    private val evaluator: SpkType2Evaluator

    init {
        val reader = SpkDafReader(file)
        index = reader.readIndex()

        order = when (index.fileFormat) {
            "LTL-IEEE" -> ByteOrder.LITTLE_ENDIAN
            "BIG-IEEE" -> ByteOrder.BIG_ENDIAN
            else -> error("Unsupported DAF format: ${index.fileFormat}")
        }

        evaluator = SpkType2Evaluator(raf, order)
    }

    override fun position(target: Int, center: Int, etSeconds: Double): Vec3Km {
        val seg = pickSegment(target, center, etSeconds)
            ?: error("No SPK segment for target=$target center=$center at ET=$etSeconds")

        return evaluator.evaluate(seg, etSeconds)
    }

    fun earthWrtSsb(etSeconds: Double): Vec3Km {
        val earthWrtBary = position(399, 3, etSeconds)
        val baryWrtSsb = position(3, 0, etSeconds)
        return earthWrtBary + baryWrtSsb
    }

    private fun pickSegment(target: Int, center: Int, etSeconds: Double): SpkSegmentSummary? {
        return index.segments.firstOrNull { s ->
            s.target == target &&
                    s.center == center &&
                    etSeconds >= s.startEt &&
                    etSeconds <= s.endEt &&
                    s.spkType == 2 &&
                    s.frame == 1
        }
    }

    fun close() {
        raf.close()
    }
}
