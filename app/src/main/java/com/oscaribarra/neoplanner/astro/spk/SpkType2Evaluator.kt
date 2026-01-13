package com.oscaribarra.neoplanner.astro.spk

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * SPK Type 2 evaluator (Chebyshev position-only).
 *
 * Segment layout:
 * - Records: N records of RSIZE doubles each, starting at dataStartAddress
 * - At the end of segment (last 4 doubles): INIT, INTLEN, RSIZE, N
 *
 * Each record:
 *   MID, RADIUS, then coefficients:
 *     X[0..nCoeff-1], Y[0..nCoeff-1], Z[0..nCoeff-1]
 * where nCoeff = (RSIZE - 2) / 3
 *
 * tau = (ET - MID) / RADIUS  in [-1, +1]
 */
class SpkType2Evaluator(
    private val raf: RandomAccessFile,
    private val byteOrder: ByteOrder
) {

    data class Type2Dir(
        val init: Double,
        val intlen: Double,
        val rsize: Int,
        val n: Int
    )

    fun evaluate(seg: SpkSegmentSummary, etSeconds: Double): Vec3Km {
        require(seg.spkType == 2) { "Unsupported SPK type ${seg.spkType}; expected 2" }

        val dir = readDirectory(seg)

        val n = dir.n
        val rsize = dir.rsize
        val init = dir.init
        val intlen = dir.intlen

        // Choose record index
        var rec = floor((etSeconds - init) / intlen).toInt()
        if (rec < 0) rec = 0
        if (rec > n - 1) rec = n - 1

        val recordStartAddr = seg.dataStartAddress + rec * rsize

        val mid = readDoubleAtWord(recordStartAddr)
        val radius = readDoubleAtWord(recordStartAddr + 1)

        val tau = (etSeconds - mid) / radius

        val nCoeff = (rsize - 2) / 3
        require(nCoeff > 0) { "Invalid type 2 record size: rsize=$rsize" }

        val xCoeffs = DoubleArray(nCoeff) { i -> readDoubleAtWord(recordStartAddr + 2 + i) }
        val yCoeffs = DoubleArray(nCoeff) { i -> readDoubleAtWord(recordStartAddr + 2 + nCoeff + i) }
        val zCoeffs = DoubleArray(nCoeff) { i -> readDoubleAtWord(recordStartAddr + 2 + 2 * nCoeff + i) }

        val x = chebyshevEval(xCoeffs, tau)
        val y = chebyshevEval(yCoeffs, tau)
        val z = chebyshevEval(zCoeffs, tau)

        return Vec3Km(x, y, z)
    }

    fun readDirectory(seg: SpkSegmentSummary): Type2Dir {
        // Last 4 doubles of the segment are: INIT, INTLEN, RSIZE, N
        val init = readDoubleAtWord(seg.dataEndAddress - 3)
        val intlen = readDoubleAtWord(seg.dataEndAddress - 2)
        val rsizeD = readDoubleAtWord(seg.dataEndAddress - 1)
        val nD = readDoubleAtWord(seg.dataEndAddress)

        val rsize = rsizeD.toInt()
        val n = nD.toInt()

        require(rsize > 2) { "Bad RSIZE=$rsize" }
        require(n > 0) { "Bad N=$n" }

        return Type2Dir(init = init, intlen = intlen, rsize = rsize, n = n)
    }

    private fun readDoubleAtWord(wordAddress1Based: Int): Double {
        val pos = (wordAddress1Based.toLong() - 1L) * 8L
        val bytes = ByteArray(8)
        raf.seek(pos)
        raf.readFully(bytes)
        val bb = ByteBuffer.wrap(bytes).order(byteOrder)
        return bb.double
    }

    /**
     * Clenshaw-like recurrence for Chebyshev series evaluation.
     * Coeffs are for T0..T(n-1).
     */
    private fun chebyshevEval(c: DoubleArray, tau: Double): Double {
        // Standard recurrence for Chebyshev polynomials of first kind
        // Evaluate sum_{k=0}^{n-1} c[k]*T_k(tau)
        if (c.isEmpty()) return 0.0
        if (c.size == 1) return c[0]

        var Tkm2 = 1.0          // T0
        var Tkm1 = tau          // T1
        var sum = c[0] * Tkm2 + c[1] * Tkm1

        for (k in 2 until c.size) {
            val Tk = 2.0 * tau * Tkm1 - Tkm2
            sum += c[k] * Tk
            Tkm2 = Tkm1
            Tkm1 = Tk
        }
        return sum
    }
}
