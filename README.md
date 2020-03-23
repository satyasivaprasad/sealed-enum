# sealed-enum (Beta)
*Enums are dead, long live enums!*

![CI-Status](https://travis-ci.com/livefront/sealed-enum.svg?token=WuckG7RmGXoNTzG4QzVk&branch=master)
[![Release](https://jitpack.io/v/livefront/sealed-enum.svg)](https://jitpack.io/#livefront/sealed-enum)

This tool is currently in beta, while any issues are worked through. Please feel free to try it out and report any bugs that you may encounter.

[Enums in Kotlin](https://kotlinlang.org/docs/reference/enum-classes.html) are quite useful for managing state and control flows, especially in combination with [`when`](https://kotlinlang.org/docs/reference/control-flow.html#when-expression).

However, enums have a few drawbacks:
- [Enum classes can't have type parameters](https://discuss.kotlinlang.org/t/enum-class-with-type-parameters/)
- Enum classes can only implement interfaces, and can't be subclasses of other classes
- As a consequence of the above, enum classes can't have hierarchies (unlike [sealed classes](https://kotlinlang.org/docs/reference/sealed-classes.html))
- The full list of values can only be retrieved [generically in a reified way](https://kotlinlang.org/docs/reference/enum-classes.html#working-with-enum-constants)

Kotlin also has [sealed classes](https://kotlinlang.org/docs/reference/sealed-classes.html), which are
> ... in a sense, an extension of enum classes: the set of values for an enum type is also restricted, but each enum constant exists only as a single instance, whereas a subclass of a sealed class can have multiple instances which can contain state.

Sealed classes are certainly more powerful than enums, with a lot of the same benefits and can also be used to great effect with `when`.
However, the only way to retrieve a full list of a sealed class's subclasses [is with reflection](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/sealed-subclasses.html) and they have no inherent ordinal value.

Now, suppose you have a sealed class that only has `object` subclasses (or a sealed subclass with only `object` subclasses, ad infinitum).

This restriction would allow defining a `values` list without any `KClass`s, with ordinals naturally derived from the order of the list.
For more complex hierarchies, the `values` list can be a well-defined order based on a [traversal of the sealed class hierarchy tree](https://en.wikipedia.org/wiki/Tree_traversal).

Creating these lists manually [is possible](https://discuss.kotlinlang.org/t/list-of-sealed-class-objects), but maintaining them is error prone and doesn't solve the problem generically.

This annotation processor automatically creates and maintains those lists to allow sealed classes of only objects (sealed enums, if you will) to be strictly more feature-rich than normal enums.

### Usage

By applying `@GenSealedEnum` to a sealed class with only objects, an object implementing `SealedEnum` for that sealed class will be generated.

For example,
```kotlin
@GenSealedEnum
sealed class Alpha {
    object Beta : Alpha()
    object Gamma : Alpha()
}
```

will generate the following object:
```kotlin
object AlphaSealedEnum : SealedEnum<Alpha> {
    override val values: List<Alpha> = listOf(
        Alpha.Beta,
        Alpha.Gamma
    )

    override fun ordinalOf(obj: Alpha): Int = when (obj) {
        Alpha.Beta -> 0
        Alpha.Gamma -> 1
    }
}
```

For nested hierarchies, the traversal order can be manually specified via `traversalOrder`, with a default value of `TreeTraversalOrder.IN_ORDER`. Multiple objects for different traversal orders can also be generated by repeating the annotation:
```kotlin
@GenSealedEnum(traversalOrder = TreeTraversalOrder.IN_ORDER)
@GenSealedEnum(traversalOrder = TreeTraversalOrder.LEVEL_ORDER)
sealed class Alpha {
    sealed class Beta : Alpha() {
        object Gamma : Beta()
    }
    object Delta : Alpha()
    sealed class Epsilon : Alpha() {
        object Zeta : Epsilon()
    }
}
```
will generate two objects:
```kotlin
object AlphaLevelOrderSealedEnum : SealedEnum<Alpha> {
    override val values: List<Alpha> = listOf(
        Alpha.Delta,
        Alpha.Beta.Gamma,
        Alpha.Epsilon.Zeta
    )

    override fun ordinalOf(obj: Alpha): Int = when (obj) {
        Alpha.Delta -> 0
        Alpha.Beta.Gamma -> 1
        Alpha.Epsilon.Zeta -> 2
    }
}

object AlphaInOrderSealedEnum : SealedEnum<Alpha> {
    override val values: List<Alpha> = listOf(
        Alpha.Beta.Gamma,
        Alpha.Delta,
        Alpha.Epsilon.Zeta
    )

    override fun ordinalOf(obj: Alpha): Int = when (obj) {
        Alpha.Beta.Gamma -> 0
        Alpha.Delta -> 1
        Alpha.Epsilon.Zeta -> 2
    }
}
```

The annotation processor also supports hooking into the `Metadata` annotation that is applied on all Kotlin classes.
By setting the following annotation processor option, `SealedEnum` implementations will be generated for all applicable sealed classes:
```kotlin
kapt {
    arguments {
        arg("sealedenum.autoGenerateSealedEnums", "true")
    }
}
```

### Installation
`sealed-enum` can be installed via gradle:

```kotlin
plugins {
    kotlin("kapt")
}

repositories {
    maven(url="https://jitpack.io")
}

dependencies {
    implementation("com.github.livefront.sealed-enum:sealedenum:0.1.0")
    kapt("com.github.livefront.sealed-enum:sealedenumprocessor:0.1.0")
}
```

### License
```
Copyright 2020 Livefront

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```