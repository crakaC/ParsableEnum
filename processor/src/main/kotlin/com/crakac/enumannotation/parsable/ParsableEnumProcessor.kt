package com.crakac.enumannotation.parsable

import com.crakac.enumannotation.ParsableEnum
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

internal class ParsableEnumProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ParsableEnum::class.qualifiedName!!)
        val ret = symbols.filter { !it.validate() }.toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(ClassVisitor(), Unit) }
        return ret
    }

    private inner class ClassVisitor : KSVisitorVoid() {
        private lateinit var fallbackName: String
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            fallbackName = classDeclaration.annotations.first {
                it.annotationType.resolve().declaration.simpleName.asString() == ParsableEnum::class.simpleName
            }.arguments[0].value as String
            classDeclaration.primaryConstructor!!.accept(this, data)
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            check(function.isConstructor())
            check(function.parameters.isNotEmpty())
            val classDeclaration = function.parentDeclaration!! as KSClassDeclaration
            val className = classDeclaration.toClassName()
            val packageName = classDeclaration.containingFile!!.packageName.asString()
            val parserClassName = "${classDeclaration.simpleName.asString()}_Parser"
            val fileSpecBuilder = FileSpec.builder(packageName, parserClassName)
            val classBuilder = TypeSpec.classBuilder(parserClassName)
            val enumParameterName = function.parameters[0].name!!
            val enumParameterType = function.parameters[0].type.toTypeName()
            val funSpecBuilder = FunSpec.builder("parse")
                .addParameter(ParameterSpec.builder("rawValue", enumParameterType).build())
                .returns(classDeclaration.toClassName())
            if (fallbackName.isNotEmpty()) {
                funSpecBuilder.addStatement(
                    "return %T.entries.find{ it.%N == `rawValue`} ?: %T.$fallbackName",
                    className,
                    enumParameterName.asString(),
                    className
                )
            } else {
                funSpecBuilder.addStatement(
                    "return %T.entries.first{ it.%N == `rawValue`}",
                    className,
                    enumParameterName.asString()
                )
            }
            val companion = TypeSpec.companionObjectBuilder()
                .addFunction(funSpecBuilder.build())
                .build()
            classBuilder.addType(companion)
            fileSpecBuilder.addType(classBuilder.build())
            fileSpecBuilder.build()
                .writeTo(codeGenerator, Dependencies(true, classDeclaration.containingFile!!))
        }
    }
}

internal class ParsableEnumProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ParsableEnumProcessor(environment.codeGenerator, environment.logger)
    }
}