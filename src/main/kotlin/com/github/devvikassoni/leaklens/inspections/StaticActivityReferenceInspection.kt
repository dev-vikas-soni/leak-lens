package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UField
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Activity, Fragment, View, Dialog or (non-Application) Context references held in static
 * fields, Kotlin `object`/companion properties or top-level properties.
 */
class StaticActivityReferenceInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Activity/Fragment/View/Context stored in static field"
    override fun getShortName() = "LeakLensStaticActivityReference"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitField(node: UField): Boolean {
                    if (!node.isStatic || !AndroidTypes.isLifecycleBoundType(node.type)) return false
                    if (AndroidTypes.hasApplicationContextQualifier(node.sourcePsi ?: node.javaPsi)) return false
                    val anchor = node.uastAnchor?.sourcePsi ?: node.sourcePsi ?: return false
                    val typeName = node.type.presentableText.removeSuffix("?")

                    reporter.report(
                        anchor,
                        "LeakLens: Static field '${node.name}' holds a $typeName. " +
                            "Static fields outlive the Activity lifecycle, so the whole UI hierarchy leaks.",
                        Finding(
                            key = LeakReporter.memberKey(node, node.name),
                            title = "Static field '${node.name}' holds $typeName",
                            location = "Static field ${node.name}",
                            retainedClass = node.type.canonicalText,
                            suggestedFix = "Do not keep UI objects in static state. Pass them as parameters, " +
                                "use applicationContext for app-wide needs, or clear the field in onDestroy().",
                        ),
                    )
                    return false
                }
            },
            arrayOf(UField::class.java)
        )
}
