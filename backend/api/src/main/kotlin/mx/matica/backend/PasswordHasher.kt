package mx.matica.backend

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id con parámetros fijados por el servidor. El hash se almacena con sus parámetros para
 * poder endurecerlos después sin invalidar las cuentas existentes.
 */
internal class PasswordHasher(
    private val memoryKb: Int = 65_536,
    private val iterations: Int = 3,
    private val parallelism: Int = 1,
    private val saltBytes: Int = 16,
    private val hashBytes: Int = 32,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(password: String): String {
        val salt = ByteArray(saltBytes).also(random::nextBytes)
        val derived = derive(password, salt, memoryKb, iterations, parallelism, hashBytes)
        val encoder = Base64.getEncoder()
        return buildString {
            append("argon2id")
            append("\$v=19")
            append("\$m=$memoryKb,t=$iterations,p=$parallelism")
            append('$').append(encoder.encodeToString(salt))
            append('$').append(encoder.encodeToString(derived))
        }
    }

    /** `false` ante cualquier hash corrupto o algoritmo desconocido; nunca lanza hacia la ruta. */
    fun verify(password: String, encoded: String): Boolean = try {
        val parts = encoded.split('$')
        if (parts.size != 5 || parts[0] != "argon2id" || parts[1] != "v=19") {
            false
        } else {
            val params = parts[2].split(',').associate { token ->
                val (name, value) = token.split('=', limit = 2)
                name to value.toInt()
            }
            val memory = params["m"] ?: return false
            val rounds = params["t"] ?: return false
            val lanes = params["p"] ?: return false
            if (memory <= 0 || rounds <= 0 || lanes <= 0) return false
            val salt = Base64.getDecoder().decode(parts[3])
            val expected = Base64.getDecoder().decode(parts[4])
            if (salt.isEmpty() || expected.isEmpty()) return false
            val derived = derive(password, salt, memory, rounds, lanes, expected.size)
            MessageDigest.isEqual(expected, derived)
        }
    } catch (_: Exception) {
        false
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        memory: Int,
        rounds: Int,
        lanes: Int,
        length: Int,
    ): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memory)
            .withIterations(rounds)
            .withParallelism(lanes)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(length).also { generator.generateBytes(password.toCharArray(), it) }
    }
}
