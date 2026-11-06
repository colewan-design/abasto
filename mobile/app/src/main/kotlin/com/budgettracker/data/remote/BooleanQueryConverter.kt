package com.budgettracker.data.remote

import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Sends booleans in query strings as `1` and `0`.
 *
 * Retrofit's default is `toString()`, which produces `true` and `false` —
 * and Laravel's `boolean` validation rule rejects both. It accepts only
 * `true`/`false` as real JSON booleans, or the strings `"1"` and `"0"`, so a
 * query like `?collapse_transfers=true` comes back as a 422 complaining that
 * the field must be true or false.
 *
 * Doing it here, once, means every endpoint's flags are encoded the same way
 * and no call site has to remember.
 */
object BooleanQueryConverter : Converter.Factory() {

    private val converter = Converter<Boolean, String> { value -> if (value) "1" else "0" }

    override fun stringConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, String>? = when (type) {
        Boolean::class.java, Boolean::class.javaObjectType -> converter
        else -> null
    }
}
