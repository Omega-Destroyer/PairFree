package io.github.omegadestroyer.pairfree.protocol.skullcandy

/**
 * AES-128 without java.*: the protocol module stays free of it so it can go multiplatform. Only what BES's TOTA channel needs (docs/skullcandy/PROTOCOL.md section 12.2): CBC
 * with no padding of its own (the caller pads with zeros to the block size) and a fixed initialisation vector. Checked against FIPS-197 and NIST SP 800-38A.
 */
internal object Aes128 {
    private val SBOX = IntArray(256)
    private val INV = IntArray(256)

    init {
        // the S-box from the field inverse and the affine map, so no table is retyped
        var p = 1
        var q = 1
        do {
            p = p xor ((p shl 1) xor (if (p and 0x80 != 0) 0x11B else 0)) and 0xFF
            q = q xor (q shl 1)
            q = q xor (q shl 2)
            q = q xor (q shl 4)
            q = q and 0xFF
            if (q and 0x80 != 0) q = q xor 0x09
            val x = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
            SBOX[p] = (x xor 0x63) and 0xFF
        } while (p != 1)
        SBOX[0] = 0x63
        for (i in 0 until 256) INV[SBOX[i]] = i
    }

    private fun rotl8(x: Int, n: Int): Int = ((x shl n) or (x ushr (8 - n))) and 0xFF

    private fun xtime(x: Int): Int = ((x shl 1) xor (if (x and 0x80 != 0) 0x11B else 0)) and 0xFF

    private fun mul(a: Int, b: Int): Int {
        var r = 0
        var x = a
        var y = b
        while (y != 0) {
            if (y and 1 != 0) r = r xor x
            x = xtime(x)
            y = y ushr 1
        }
        return r
    }

    private fun expand(key: ByteArray): Array<IntArray> {
        require(key.size == 16) { "an AES-128 key is 16 bytes" }
        val w = Array(44) { IntArray(4) }
        for (i in 0 until 4) for (j in 0 until 4) w[i][j] = key[4 * i + j].toInt() and 0xFF
        var rcon = 1
        for (i in 4 until 44) {
            val t = w[i - 1].copyOf()
            if (i % 4 == 0) {
                val r = intArrayOf(SBOX[t[1]], SBOX[t[2]], SBOX[t[3]], SBOX[t[0]])
                r[0] = r[0] xor rcon
                rcon = xtime(rcon)
                for (j in 0 until 4) t[j] = r[j]
            }
            for (j in 0 until 4) w[i][j] = w[i - 4][j] xor t[j]
        }
        return w
    }

    private fun addRoundKey(s: IntArray, w: Array<IntArray>, round: Int) {
        for (c in 0 until 4) for (r in 0 until 4) s[4 * c + r] = s[4 * c + r] xor w[4 * round + c][r]
    }

    private fun encryptBlock(block: ByteArray, w: Array<IntArray>): ByteArray {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, w, 0)
        for (round in 1..10) {
            for (i in 0 until 16) s[i] = SBOX[s[i]]
            val t = s.copyOf()
            for (c in 0 until 4) for (r in 0 until 4) s[4 * c + r] = t[4 * ((c + r) % 4) + r]
            if (round != 10) {
                for (c in 0 until 4) {
                    val a = IntArray(4) { s[4 * c + it] }
                    s[4 * c] = mul(a[0], 2) xor mul(a[1], 3) xor a[2] xor a[3]
                    s[4 * c + 1] = a[0] xor mul(a[1], 2) xor mul(a[2], 3) xor a[3]
                    s[4 * c + 2] = a[0] xor a[1] xor mul(a[2], 2) xor mul(a[3], 3)
                    s[4 * c + 3] = mul(a[0], 3) xor a[1] xor a[2] xor mul(a[3], 2)
                }
            }
            addRoundKey(s, w, round)
        }
        return ByteArray(16) { s[it].toByte() }
    }

    private fun decryptBlock(block: ByteArray, w: Array<IntArray>): ByteArray {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, w, 10)
        for (round in 9 downTo 0) {
            val t = s.copyOf()
            for (c in 0 until 4) for (r in 0 until 4) s[4 * ((c + r) % 4) + r] = t[4 * c + r]
            for (i in 0 until 16) s[i] = INV[s[i]]
            addRoundKey(s, w, round)
            if (round != 0) {
                for (c in 0 until 4) {
                    val a = IntArray(4) { s[4 * c + it] }
                    s[4 * c] = mul(a[0], 14) xor mul(a[1], 11) xor mul(a[2], 13) xor mul(a[3], 9)
                    s[4 * c + 1] = mul(a[0], 9) xor mul(a[1], 14) xor mul(a[2], 11) xor mul(a[3], 13)
                    s[4 * c + 2] = mul(a[0], 13) xor mul(a[1], 9) xor mul(a[2], 14) xor mul(a[3], 11)
                    s[4 * c + 3] = mul(a[0], 11) xor mul(a[1], 13) xor mul(a[2], 9) xor mul(a[3], 14)
                }
            }
        }
        return ByteArray(16) { s[it].toByte() }
    }

    /** One block (16 bytes) in the electronic codebook mode, for the tests. */
    fun encryptBlock(key: ByteArray, block: ByteArray): ByteArray = encryptBlock(block, expand(key))

    fun decryptBlock(key: ByteArray, block: ByteArray): ByteArray = decryptBlock(block, expand(key))

    /** CBC with [iv], the data first padded with zeros to a whole number of blocks. */
    fun cbcEncryptZeroPadded(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val w = expand(key)
        val padded = data.copyOf(((data.size + 15) / 16) * 16)
        val out = ByteArray(padded.size)
        var prev = iv
        for (b in 0 until padded.size / 16) {
            val block = ByteArray(16) { (padded[16 * b + it].toInt() xor prev[it].toInt()).toByte() }
            val enc = encryptBlock(block, w)
            enc.copyInto(out, 16 * b)
            prev = enc
        }
        return out
    }

    /** CBC with [iv]; [data] must be whole blocks. The zeros of the padding stay in the result. */
    fun cbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "a CBC ciphertext is whole blocks" }
        val w = expand(key)
        val out = ByteArray(data.size)
        var prev = iv
        for (b in 0 until data.size / 16) {
            val block = data.copyOfRange(16 * b, 16 * b + 16)
            val dec = decryptBlock(block, w)
            for (i in 0 until 16) out[16 * b + i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            prev = block
        }
        return out
    }
}
