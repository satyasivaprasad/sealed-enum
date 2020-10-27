package com.livefront.sealedenum.internal.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSType

internal fun KSClassDeclaration.asType(): KSType = asType(emptyList())

internal fun KSAnnotated.findAnnotationsWithType(target: KSType): List<KSAnnotation> =
    annotations.filter { it.annotationType.resolve() == target }

/**
 * All sealed subclasses will be in the file, somewhere.
 *
 * We only want each sealed subclass once, and we want the sealed subclasses declared outside of the sealed class to
 * come first.
 */
internal fun KSClassDeclaration.sealedSubclasses(): List<KSClassDeclaration> =
    (containingFile?.allTypes()?.filter { it.superTypes.firstOrNull()?.resolve()?.declaration == this }.orEmpty() +
            allTypes().filter { it.superTypes.firstOrNull()?.resolve()?.declaration == this })
        // We want to pick up the sealed subclasses declared in the sealed class, but they are also going to be in the
        // containing file. Reverse the list for `distinct`.
        .reversed()
        .distinct()
        // Reverse the list back, to restore the order we want.
        .reversed()

private fun KSDeclarationContainer.allTypes(): List<KSClassDeclaration> =
    listOfNotNull(this as? KSClassDeclaration) +
            declarations.filterIsInstance<KSClassDeclaration>().flatMap(KSDeclarationContainer::allTypes)
