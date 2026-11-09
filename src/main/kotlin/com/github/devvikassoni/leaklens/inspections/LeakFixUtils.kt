package com.github.devvikassoni.leaklens.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/** PSI editing helpers shared by LeakLens quick fixes (all run inside the fix's write action). */
internal object LeakFixUtils {

    fun isKotlin(element: PsiElement) = element.language.id.equals("kotlin", ignoreCase = true)

    /** Adds `import fqn` to a Kotlin file unless already imported (explicitly or via `*`). */
    fun addKotlinImports(file: PsiElement?, vararg fqns: String) {
        val ktFile = file as? KtFile ?: return
        val importList = ktFile.importList ?: return
        val factory = KtPsiFactory(ktFile.project)
        for (fqn in fqns) {
            val name = FqName(fqn)
            val imported = ktFile.importDirectives.any {
                it.importedFqName == name || (it.isAllUnder && it.importedFqName == name.parent())
            }
            if (imported || ktFile.packageFqName == name.parent()) continue
            importList.add(factory.createImportDirective(ImportPath(name, false)))
        }
    }

    /** Inserts [statement] as the first (or last) statement of a Kotlin function body. */
    private fun insertKotlinStatement(function: KtNamedFunction, statement: String, atStart: Boolean) {
        val body = function.bodyBlockExpression ?: return
        val factory = KtPsiFactory(function.project)
        val expr = factory.createExpression(statement)
        if (atStart) {
            val added = body.addAfter(expr, body.lBrace ?: return)
            body.addBefore(factory.createNewLine(), added)
        } else {
            val added = body.addBefore(expr, body.rBrace ?: return)
            body.addBefore(factory.createNewLine(), added)
        }
        CodeStyleManager.getInstance(function.project).reformat(function)
    }

    /**
     * Adds [statement] to the Kotlin override [methodName], creating the override (calling super) when missing.
     */
    fun addToKotlinOverride(ktClass: KtClassOrObject, methodName: String, statement: String, atStart: Boolean) {
        val existing = ktClass.declarations.filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == methodName && it.valueParameters.isEmpty() && it.bodyBlockExpression != null }
        if (existing != null) {
            insertKotlinStatement(existing, statement, atStart)
            return
        }
        val body = if (atStart) "$statement\nsuper.$methodName()" else "super.$methodName()\n$statement"
        val function = KtPsiFactory(ktClass.project).createFunction("override fun $methodName() {\n$body\n}")
        val added = ktClass.addDeclaration(function)
        CodeStyleManager.getInstance(ktClass.project).reformat(added)
    }

    /**
     * Adds [statement] to the Java override [methodName], creating it (with [visibility] and a super call)
     * when missing.
     */
    fun addToJavaOverride(
        psiClass: PsiClass,
        methodName: String,
        visibility: String,
        statement: String,
        atStart: Boolean,
    ) {
        val project = psiClass.project
        val factory = JavaPsiFacade.getElementFactory(project)
        val existing: PsiMethod? = psiClass.findMethodsByName(methodName, false)
            .firstOrNull { it.parameterList.isEmpty && it.body != null }
        if (existing != null) {
            val body = existing.body ?: return
            val stmt = factory.createStatementFromText(statement, existing)
            if (atStart) body.addAfter(stmt, body.lBrace) else body.addBefore(stmt, body.rBrace)
            CodeStyleManager.getInstance(project).reformat(existing)
            return
        }
        val superCall = "super.$methodName();"
        val bodyText = if (atStart) "$statement\n$superCall" else "$superCall\n$statement"
        val method = factory.createMethodFromText(
            "@Override\n$visibility void $methodName() {\n$bodyText\n}",
            psiClass
        )
        val added = psiClass.add(method)
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(added)
        CodeStyleManager.getInstance(project).reformat(added)
    }

    fun reformat(project: Project, element: PsiElement) {
        CodeStyleManager.getInstance(project).reformat(element)
    }
}
