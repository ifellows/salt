package com.dev.salt

import android.util.Log
import java.security.MessageDigest
import kotlin.random.Random

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.apache.commons.jexl3.JexlException

fun randomHash(): String {
    // Generate a random number
    val randomNumber = Random.nextInt()

    // Convert the number to a string
    val randomString = randomNumber.toString()

    // Compute the MD5 hash
    val md = MessageDigest.getInstance("MD5")
    val hashBytes = md.digest(randomString.toByteArray())

    // Convert the hash bytes to a hexadecimal string
    return hashBytes.joinToString("") { "%02x".format(it) }
}



// Lazy-initialized JEXL engine instance for efficiency.
// This ensures the JexlEngine is created only once when first needed.
private val jexlEngine by lazy {
    JexlBuilder()
        .cache(512)      // Enable expression cache for better performance on repeated scripts.
        .strict(true)    // Enforce strict behavior (e.g., undefined variables cause errors). Recommended.
        .silent(false)   // If true, errors are suppressed and return null. If false, JexlExceptions are thrown.
        // Setting to false allows for more explicit error handling in the catch blocks.
        .create()
}


/**
 * Evaluates a JEXL (Java Expression Language) script string against a given data context.
 *
 * This function can be used to execute simple logical statements or expressions defined
 * in a string format, using data provided in the `contextData` map.
 *
 * @param script The JEXL script string to evaluate.
 *               If the script is null or blank, this function will return null.
 * @param contextData A map where keys are variable names that can be used within the
 *                    JEXL script, and values are the corresponding data for those variables.
 * @return The result of the script evaluation as `Any?` (meaning it can be any type, or null).
 *         Returns null if:
 *         - The input `script` is null or blank.
 *         - An error occurs during script parsing or evaluation (an error will be logged).
 */
fun evaluateJexlScript(script: String?, contextData: Map<String, Any>): Any? {
    // If the script string is null or effectively empty, no evaluation is performed.
    if (script.isNullOrBlank()) {
        Log.d("JexlEvaluate", "Script is null or blank. No evaluation needed, returning null.")
        return null // Or you might define a default return value based on your application's logic.
    }

    return try {
        // Create a JEXL expression object from the script string.
        // The jexlEngine (being cached) might return a pre-compiled expression if seen before.
        val expression = jexlEngine.createExpression(script)

        // Create a JEXL context, wrapping the provided contextData map.
        // This makes the keys in contextData available as variables in the script.
        val context = MapContext(contextData)

        // Evaluate the expression against the context.
        val result = expression.evaluate(context)

        Log.d("JexlEvaluate", "Successfully evaluated script '$script'. Result: $result")
        result // Return the outcome of the evaluation.
    } catch (e: JexlException.Variable) {
        // Handles errors where the script refers to a variable not defined in the contextData.
        Log.e("JexlEvaluate", "JEXL Error: Undefined variable '${e.variable}' in script '$script'. Available context keys: ${contextData.keys}", e)
        null // Return null to indicate an error.
    } catch (e: JexlException.Method) {
        // Handles errors where the script tries to call a method that doesn't exist or isn't accessible.
        Log.e("JexlEvaluate", "JEXL Error: Undefined method '${e.method}' in script '$script'. Available context keys: ${contextData.keys}", e)
        null
    } catch (e: JexlException.Parsing) {
        // Handles syntax errors within the JEXL script itself.
        Log.e("JexlEvaluate", "JEXL Parsing Error: '${e.message}' for script '$script'", e)
        null
    } catch (e: JexlException) {
        // A general catch block for any other JEXL-specific exceptions.
        Log.e("JexlEvaluate", "A JEXL evaluation error occurred for script '$script'. Context keys: ${contextData.keys}", e)
        null
    } catch (e: Exception) {
        // A fallback catch block for any other unexpected exceptions during the process.
        Log.e("JexlEvaluate", "Unexpected error during JEXL script evaluation for script '$script'. Context keys: ${contextData.keys}", e)
        null
    }
}