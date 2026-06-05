package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.openapi.project.Project

/**
 * Detects View references held in Fragment fields that are not nulled out in onDestroyView.
 */
class ViewReferenceHeldInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "View reference held beyond lifecycle in Fragment"
    override fun getShortName() = "LeakLensViewReferenceHeld"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!LeakLensInspectionUtils.isFragment(node)) return false

                    val viewFields = node.fields.filter { 
                        LeakLensInspectionUtils.isViewOrBindingType(it.type) 
                    }
                    if (viewFields.isEmpty()) return false

                    val onDestroyView = node.methods.find { it.name == "onDestroyView" }
                    val bodyText = onDestroyView?.uastBody?.asSourceString() ?: ""

                    for (field in viewFields) {
                        val name = field.name
                        val isNulled = bodyText.contains("$name = null") ||
                                       bodyText.contains("$name=null") ||
                                       bodyText.contains("_$name = null")
                        
                        if (!isNulled) {
                            val elementToHighlight = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            holder.registerProblem(
                                elementToHighlight,
                                "LeakLens: View field '$name' is not nulled in onDestroyView().",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                NullifyInOnDestroyViewFix(name)
                            )
                            
                            if (isOnTheFly) {
                                fileIssues.add(createLeakInfo(field))
                            }
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                     LeakLensInspectionUtils.reportLiveIssue(holder, isOnTheFly, "ViewReferenceHeld", fileIssues)
                }
            },
            arrayOf(UClass::class.java, UFile::class.java)
        )
    }

    private fun createLeakInfo(field: UField): LeakInfo {
        val line = field.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "view_reference_leak_${field.name}_$line",
            shortDescription = "View reference ${field.name} not cleared",
            leakTrace = "Fragment field: ${field.name} (line $line)",
            retainedObjectClassName = "android.view.View",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Set ${field.name} = null in onDestroyView() to allow GC."
        )
    }

    private class NullifyInOnDestroyViewFix(private val fieldName: String) : LocalQuickFix {
        override fun getName() = "Nullify '$fieldName' in onDestroyView()"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val factory = JavaPsiFacade.getElementFactory(project)
            
            if (element.language.id == "JAVA") {
                val uField = element.toUElementOfType<UField>() ?: return
                val psiClass = uField.getContainingUClass()?.javaPsi ?: return
                val onDestroyView = psiClass.findMethodsByName("onDestroyView", false).firstOrNull()
                
                if (onDestroyView != null) {
                    val body = onDestroyView.body ?: return
                    body.addBefore(factory.createStatementFromText("$fieldName = null;", psiClass), body.rBrace)
                } else {
                    val newMethod = factory.createMethodFromText(
                        "@Override public void onDestroyView() { super.onDestroyView(); $fieldName = null; }", 
                        psiClass
                    )
                    psiClass.add(newMethod)
                }
            } else {
                element.parent.addBefore(factory.createCommentFromText("// LeakLens: Set $fieldName = null in onDestroyView()", element), element)
            }
        }
    }
}
