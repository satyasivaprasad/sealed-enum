package com.livefront.sealedenum.internal.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.livefront.sealedenum.GenSealedEnum
import com.livefront.sealedenum.internal.common.SealedClassNode
import com.livefront.sealedenum.internal.common.Visibility
import com.livefront.sealedenum.internal.common.areUniqueBy
import com.livefront.sealedenum.internal.common.spec.SealedEnumFileSpec
import com.livefront.sealedenum.internal.common.spec.SealedEnumFileSpec.SealedEnumOption.SealedEnumOnly
import com.livefront.sealedenum.internal.common.spec.SealedEnumFileSpec.SealedEnumOption.SealedEnumWithEnum
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.analyzer.AnalysisResult

internal const val ERROR_ELEMENT_IS_ANNOTATED_WITH_REPEATED_TRAVERSAL_ORDER =
    "Element is annotated with the same traversal order multiple times"
internal const val ERROR_ELEMENT_IS_NOT_KOTLIN_CLASS = "Annotated element is not a Kotlin class"
internal const val ERROR_ELEMENT_IS_NOT_COMPANION_OBJECT = "Annotated element is not a companion object"
internal const val ERROR_COMPANION_OBJECT_HAS_INVALID_VISIBILITY = "Annotated companion object isn't internal or public"
internal const val ERROR_ENCLOSING_ELEMENT_IS_NOT_KOTLIN_CLASS = "Enclosing element is not a Kotlin class"
internal const val ERROR_CLASS_IS_NOT_SEALED = "Annotated companion object is not for a sealed class"
internal const val ERROR_NON_OBJECT_SEALED_SUBCLASSES = "Annotated sealed class has a non-object subclass"
internal const val ERROR_SUBCLASS_HAS_INVALID_VISIBILITY = "Subclass of sealed class isn't internal or public"
internal const val ERROR_SEALED_CLASS_HAS_INVALID_VISIBILITY = "Annotated sealed class isn't internal or public"

@AutoService(SymbolProcessor::class)
public class SealedEnumProcessor : SymbolProcessor {

    private lateinit var codeGenerator: CodeGenerator
    private lateinit var logger: KSPLogger

    /**
     * TODO: Should be obsoleted by https://github.com/google/ksp/issues/122
     */
    private var encounteredError: Boolean = false

    private var isDisabled: Boolean = false

    override fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) {
        this.codeGenerator = codeGenerator
        this.logger = logger

        isDisabled = options["com.livefront.sealedenum.ksp.disabled"] == "true"
    }

    override fun process(resolver: Resolver) {
        if (isDisabled) return

        resolver
            .getSymbolsWithAnnotation(GenSealedEnum::class.qualifiedName!!)
            .distinct()
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { createSealedEnumFileSpec(resolver, it) }
            .forEach {
                it.build().writeTo(codeGenerator)
            }
    }

    override fun finish() {
        if (encounteredError) {
            throw AnalysisResult.CompilationErrorException()
        }
    }

    /**
     * Tries to create a [SealedEnumFileSpec] for the given [sealedClassCompanionObjectKSClass] that was annotated.
     *
     * If there is an error processing the given [KSClassDeclaration], a relevant error message will be printed and a
     * null [SealedEnumFileSpec] will be returned.
     */
    @Suppress("ReturnCount", "LongMethod", "ComplexMethod")
    private fun createSealedEnumFileSpec(
        resolver: Resolver,
        sealedClassCompanionObjectKSClass: KSClassDeclaration
    ): SealedEnumFileSpec? {

        val genSealedEnumClassType = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(
                GenSealedEnum::class.qualifiedName!!
            )
        )!!.asType()

        /**
         * A helper function to print the given [message] as an error.
         */
        fun printError(message: String, symbol: KSNode?) {
            logger.error(message, symbol)
            encounteredError = true
        }

        /**
         * The [GenSealedEnum] annotations used to annotate the [sealedClassCompanionObjectKSClass], represented as
         * [GenSealedEnumHolder]s.
         */
        val sealedEnumAnnotations = sealedClassCompanionObjectKSClass.findAnnotationsWithType(genSealedEnumClassType)
            .sortedByDescending { (it.location as? FileLocation)?.lineNumber ?: 0 }
            .map(GenSealedEnumHolder.Companion::fromKSAnnotation)

        // Ensure that the annotation are unique by traversal order
        if (!sealedEnumAnnotations.areUniqueBy { it.traversalOrder }) {
            printError(ERROR_ELEMENT_IS_ANNOTATED_WITH_REPEATED_TRAVERSAL_ORDER, sealedClassCompanionObjectKSClass)
            return null
        }

        if (sealedClassCompanionObjectKSClass.origin != Origin.KOTLIN) {
            printError(ERROR_ELEMENT_IS_NOT_KOTLIN_CLASS, sealedClassCompanionObjectKSClass)
            return null
        }

        if (!sealedClassCompanionObjectKSClass.isCompanionObject) {
            printError(ERROR_ELEMENT_IS_NOT_COMPANION_OBJECT, sealedClassCompanionObjectKSClass)
            return null
        }

        val sealedClassCompanionObjectVisibility = when {
            sealedClassCompanionObjectKSClass.isPublic() -> Visibility.PUBLIC
            sealedClassCompanionObjectKSClass.isInternal() -> Visibility.INTERNAL
            else -> {
                printError(ERROR_COMPANION_OBJECT_HAS_INVALID_VISIBILITY, sealedClassCompanionObjectKSClass)
                return null
            }
        }

        val sealedClassKSClass = sealedClassCompanionObjectKSClass.parentDeclaration

        if (sealedClassKSClass !is KSClassDeclaration) {
            printError(ERROR_ENCLOSING_ELEMENT_IS_NOT_KOTLIN_CLASS, sealedClassKSClass)
            return null
        }

        if (!sealedClassKSClass.modifiers.contains(Modifier.SEALED)) {
            printError(ERROR_CLASS_IS_NOT_SEALED, sealedClassKSClass)
            return null
        }

        val sealedClassVisibility = when {
            sealedClassKSClass.isPublic() -> Visibility.PUBLIC
            sealedClassKSClass.isInternal() -> Visibility.INTERNAL
            else -> {
                printError(ERROR_SEALED_CLASS_HAS_INVALID_VISIBILITY, sealedClassKSClass)
                return null
            }
        }

        /**
         * The root of the tree representing the sealed class hierarchy.
         */
        val sealedClassNode = try {
            createSealedClassNode(sealedClassKSClass)
        } catch (nonSealedClassException: NonObjectSealedSubclassException) {
            printError(ERROR_NON_OBJECT_SEALED_SUBCLASSES, nonSealedClassException.ksNode)
            return null
        } catch (invalidSubclassVisibilityException: InvalidSubclassVisibilityException) {
            printError(ERROR_SUBCLASS_HAS_INVALID_VISIBILITY, invalidSubclassVisibilityException.ksNode)
            return null
        }

        /**
         * A nullable list of interfaces that the sealed class (or any of its super classes) implement.
         * This list is only created if it will be used (that is, if `generateEnum` is true for any sealed enum seed).
         */
        val sealedClassInterfaces: List<TypeName>? = if (sealedEnumAnnotations.any { it.generateEnum }) {
            SuperInterfaces(sealedClassKSClass).getAllSuperInterfaces()
        } else {
            null
        }

        return SealedEnumFileSpec(
            sealedClass = sealedClassKSClass.toClassName(),
            sealedClassVisibility = sealedClassVisibility,
            sealedClassCompanionObject = sealedClassCompanionObjectKSClass.toClassName(),
            sealedClassCompanionObjectVisibility = sealedClassCompanionObjectVisibility,
            typeParameters = sealedClassKSClass.wildcardedTypeNames(),
            sealedClassCompanionObjectElement = null,
            sealedClassNode = sealedClassNode,
            sealedEnumOptions = sealedEnumAnnotations.associate {
                it.traversalOrder to if (it.generateEnum) {
                    SealedEnumWithEnum(sealedClassInterfaces!!)
                } else {
                    SealedEnumOnly
                }
            }
        )
    }

    /**
     * A recursive function used in concert with [convertSealedSubclassToNode] to create a
     * [SealedClassNode.SealedClass] given a [KSClassDeclaration] for the sealed class.
     */
    private fun createSealedClassNode(
        sealedClassKSClass: KSClassDeclaration
    ): SealedClassNode.SealedClass =
        SealedClassNode.SealedClass(sealedClassKSClass.sealedSubclasses().map(::convertSealedSubclassToNode))

    /**
     * A recursive function used in concert with [createSealedClassNode] to try to create a [SealedClassNode].
     *
     * If [sealedSubclassKSClass] is not public or internal, than an [InvalidSubclassVisibilityException] will be
     * thrown.
     *
     * If [sealedSubclassKSClass] is an object, then this function will return a [SealedClassNode.Object].
     * If [sealedSubclassKSClass] is another sealed class, then this function will return a
     * [SealedClassNode.SealedClass].
     * If [sealedSubclassKSClass] is neither, then this function will throw a [NonObjectSealedSubclassException]
     */
    private fun convertSealedSubclassToNode(sealedSubclassKSClass: KSClassDeclaration): SealedClassNode {
        return when {
            sealedSubclassKSClass.isPublic() || sealedSubclassKSClass.isInternal() -> when {
                sealedSubclassKSClass.classKind == ClassKind.OBJECT ->
                    SealedClassNode.Object(sealedSubclassKSClass.toClassName())
                sealedSubclassKSClass.modifiers.contains(Modifier.SEALED) ->
                    createSealedClassNode(sealedSubclassKSClass)
                else -> throw NonObjectSealedSubclassException(sealedSubclassKSClass)
            }
            else -> throw InvalidSubclassVisibilityException(sealedSubclassKSClass)
        }
    }
}
