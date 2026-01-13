package com.oscaribarra.neoplanner.astro.spk

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Minimal NAIF DAF/SPK reader:
 * - Reads the file record (first 1024 bytes)
 * - Reads summary records and extracts SPK segment summaries (ND/NI expected 2/6 for SPK)
 *
 * This does NOT yet evaluate segment data (Chebyshev). That will be the next step.
 */
class SpkDafReader(private val file: File) {

    fun readIndex(): SpkKernelIndex {
        RandomAccessFile(file, "r").use { raf ->
            val fileRec = ByteArray(1024)
            raf.seek(0)
            raf.readFully(fileRec)

            val idWord = ascii(fileRec, 0, 8).trim()
            require(idWord.startsWith("DAF/")) { "Not a DAF file (id=$idWord)" }

            // LOCFMT is ASCII at address 88, length 8
            val bff = ascii(fileRec, 88, 8).trim()
            val order = when (bff) {
                "LTL-IEEE" -> ByteOrder.LITTLE_ENDIAN
                "BIG-IEEE" -> ByteOrder.BIG_ENDIAN
                else -> error("Unsupported DAF binary format: '$bff'")
            }

            // ND/NI/FWARD/BWARD/FREE are 4-byte binary integers (not ASCII) :contentReference[oaicite:1]{index=1}
            val bb = ByteBuffer.wrap(fileRec).order(order)

            val nd = bb.getInt(8)
            val ni = bb.getInt(12)
            val fward = bb.getInt(76)
            // val bward = bb.getInt(80)
            // val free  = bb.getInt(84)

            val segments = readAllSummaries(raf, order, nd, ni, fward)

            return SpkKernelIndex(fileFormat = bff, nd = nd, ni = ni, segments = segments)
        }
    }


    private fun readAllSummaries(
        raf: RandomAccessFile,
        order: ByteOrder,
        nd: Int,
        ni: Int,
        firstSummaryRecord: Int
    ): List<SpkSegmentSummary> {

        val summaries = mutableListOf<SpkSegmentSummary>()
        var recNo = firstSummaryRecord

        while (recNo != 0) {
            val rec = readRecord(raf, recNo)
            val bb = ByteBuffer.wrap(rec).order(order)

            // Summary record layout (first 3 doubles often stored as 64-bit floats):
            // next record, prev record, nsum are stored in DAF as "floating" or "integer"
            // In SPICE DAF, these control items are stored as 3 64-bit floats in IEEE.
            val next = bb.getDouble(0).toInt()
            // val prev = bb.getDouble(8).toInt()
            val nsum = bb.getDouble(16).toInt()

            val summarySizeDoubles = nd + ((ni + 1) / 2) // integers are packed 2 per double
            val bytesPerSummary = summarySizeDoubles * 8

            // Summaries start after the 3 control doubles (24 bytes)
            var offset = 24
            repeat(nsum) {
                val startEt = bb.getDouble(offset)
                val endEt = bb.getDouble(offset + 8)

                // Integers packed 2 per 8-byte slot:
                val ints = unpackIntsFromSummary(bb, offset, nd, ni)

                val target = ints[0]
                val center = ints[1]
                val frame = ints[2]
                val spkType = ints[3]
                val addrStart = ints[4]
                val addrEnd = ints[5]

                summaries += SpkSegmentSummary(
                    startEt = startEt,
                    endEt = endEt,
                    target = target,
                    center = center,
                    frame = frame,
                    spkType = spkType,
                    dataStartAddress = addrStart,
                    dataEndAddress = addrEnd
                )

                offset += bytesPerSummary
            }

            recNo = next
        }

        return summaries
    }

    private fun unpackIntsFromSummary(
        bb: ByteBuffer,
        summaryOffset: Int,
        nd: Int,
        ni: Int
    ): IntArray {
        // Integers begin after ND doubles
        val intOffset = summaryOffset + nd * 8
        val out = IntArray(ni)

        var k = 0
        var pos = intOffset
        while (k < ni) {
            val word = bb.getLong(pos)
            val (a, b) = unpackTwoInt32FromWord(word, bb.order())
            out[k] = a
            if (k + 1 < ni) out[k + 1] = b
            k += 2
            pos += 8
        }

        return out
    }

    /**
     * DAF packs two 32-bit signed integers into one 64-bit word.
     * Many implementations treat the 64-bit storage as raw bits.
     */
    private fun unpackTwoInt32FromWord(word: Long, order: ByteOrder): Pair<Int, Int> {
        val hi = (word ushr 32).toInt()
        val lo = (word and 0xFFFFFFFFL).toInt()

        return when (order) {
            ByteOrder.BIG_ENDIAN -> Pair(hi, lo)
            ByteOrder.LITTLE_ENDIAN -> Pair(lo, hi)
            else -> Pair(hi, lo)
        }
    }


    private fun readRecord(raf: RandomAccessFile, recordNumber1Based: Int): ByteArray {
        val buf = ByteArray(1024)
        val offset = (recordNumber1Based - 1L) * 1024L
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    private fun ascii(bytes: ByteArray, start: Int, len: Int): String =
        String(bytes, start, len, Charsets.US_ASCII)
}
