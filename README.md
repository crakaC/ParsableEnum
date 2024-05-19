A Kotlin symbol processor that automatically generates getters and setters of enum class for
annotated properties.

# Sample

## String / List<String>
```kotlin
@ParsableEnum(fallback = "Unknown")
enum class StringEnum(val stringValue: String = "") {
    A("a"),
    B("b"),
    C("c"),
    Unknown;
}

data class A(
    @EnumAccessor("type", StringEnum::class)
    val stringValue: String,
    @EnumAccessor("types", StringEnum::class)
    var stringValues: List<String>,
)
```

## Int / List<Int>
```kotlin
@ParsableEnum(fallback = "Unknown")
enum class IntEnum(val v: Int) {
    One(1),
    Two(2),
    Unknown(Int.MIN_VALUE)
}

data class B(
    @EnumAccessor("kind", IntEnum::class)
    val intValue: Int,
    @EnumAccessor("kinds", IntEnum::class)
    var intValues: List<Int>
)
```

# Limitation
KSP does not allow code modification, so it is not possible to generate getters and setters for private properties.

# Issues
If annotated property is nullable, it may fail to generate accessors 