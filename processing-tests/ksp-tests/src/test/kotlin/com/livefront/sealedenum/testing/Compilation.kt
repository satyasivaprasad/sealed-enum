package com.livefront.sealedenum.testing

import com.livefront.sealedenum.internal.ksp.SealedEnumProcessor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessors
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Compiles the given [sourceFiles] with the application of [SealedEnumProcessor].
 *
 * TODO: Due to https://github.com/tschuchortdev/kotlin-compile-testing/issues/69, errors logged to KSP are not
 *       currently included in the normal [KotlinCompilation.Result.messages]. As a workaround, we redirect [System.err]
 *       during compilation, and separately record the messages into our own [CompilationResult].
 */
internal fun compile(vararg sourceFiles: SourceFile): CompilationResult {
    val output = ByteArrayOutputStream()
    val printOut = PrintStream(output)
    val oldError = System.err
    System.setErr(printOut)

    val result = KotlinCompilation().apply {
        sources = sourceFiles.toList()
        messageOutputStream = printOut
        symbolProcessors = listOf(SealedEnumProcessor())
        inheritClassPath = true
    }.compile()

    System.setErr(oldError)

    return CompilationResult(
        kotlinCompilationResult = result,
        messages = output.toString()
    )
}

