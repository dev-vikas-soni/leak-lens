package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * Navigates to source code from leak trace references.
 * Uses JavaPsiFacade to resolve class names to PSI elements.
 */
@Service(Service.Level.PROJECT)
class SourceNavigationService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Navigate to a class in the editor.
     */
    fun navigateToClass(className: String) {
        ApplicationManager.getApplication().invokeLater {
            val psiClass = ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiClass?> {
                JavaPsiFacade.getInstance(project)
                    .findClass(className, GlobalSearchScope.allScope(project))
            }

            if (psiClass != null) {
                val virtualFile = psiClass.containingFile?.virtualFile
                if (virtualFile != null) {
                    val offset = psiClass.textOffset
                    OpenFileDescriptor(project, virtualFile, offset).navigate(true)
                    logger.info("LeakLens: Navigated to class $className")
                }
            } else {
                logger.warn("LeakLens: Could not find class $className in project")
            }
        }
    }

    /**
     * Navigate to a specific field/method within a class.
     */
    fun navigateToReference(reference: LeakTraceReference) {
        ApplicationManager.getApplication().invokeLater {
            val psiClass = ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiClass?> {
                JavaPsiFacade.getInstance(project)
                    .findClass(reference.owningClassName, GlobalSearchScope.allScope(project))
            }

            if (psiClass != null) {
                ApplicationManager.getApplication().runReadAction {
                    // Try to find the field
                    val field = psiClass.findFieldByName(reference.referenceName, false)
                    if (field != null) {
                        val virtualFile = field.containingFile?.virtualFile
                        if (virtualFile != null) {
                            ApplicationManager.getApplication().invokeLater {
                                OpenFileDescriptor(project, virtualFile, field.textOffset).navigate(true)
                            }
                            return@runReadAction
                        }
                    }

                    // Try to find method
                    val methods = psiClass.findMethodsByName(reference.referenceName, false)
                    if (methods.isNotEmpty()) {
                        val method = methods.first()
                        val virtualFile = method.containingFile?.virtualFile
                        if (virtualFile != null) {
                            ApplicationManager.getApplication().invokeLater {
                                OpenFileDescriptor(project, virtualFile, method.textOffset).navigate(true)
                            }
                            return@runReadAction
                        }
                    }

                    // Fall back to class navigation
                    val virtualFile = psiClass.containingFile?.virtualFile
                    if (virtualFile != null) {
                        ApplicationManager.getApplication().invokeLater {
                            OpenFileDescriptor(project, virtualFile, psiClass.textOffset).navigate(true)
                        }
                    }
                }
            } else {
                logger.warn("LeakLens: Could not find class ${reference.owningClassName}")
            }
        }
    }

    /**
     * Check if a class exists in the project scope.
     */
    fun isClassInProject(className: String): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.projectScope(project)) != null
        }
    }

    companion object {
        fun getInstance(project: Project): SourceNavigationService =
            project.getService(SourceNavigationService::class.java)
    }
}

