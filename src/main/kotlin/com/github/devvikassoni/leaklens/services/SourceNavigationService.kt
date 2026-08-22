package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * Service responsible for resolving and navigating to source code locations.
 *
 * It maps leak trace components (classes, fields, methods) to physical source positions
 * using the project's PSI index. Operations are performed asynchronously to maintain
 * IDE responsiveness.
 */
@Service(Service.Level.PROJECT)
class SourceNavigationService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Navigate to a class in the editor.
     */
    fun navigateToClass(className: String) {
        com.intellij.openapi.application.ReadAction.nonBlocking<OpenFileDescriptor?> {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project))
                ?: return@nonBlocking null

            val virtualFile = psiClass.containingFile?.virtualFile ?: return@nonBlocking null
            OpenFileDescriptor(project, virtualFile, psiClass.textOffset)
        }
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { descriptor ->
                descriptor?.navigate(true)
                if (descriptor != null) logger.info("LeakLens: Navigated to class $className")
            }.submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
    }

    /**
     * Navigate to a specific field/method within a class.
     */
    fun navigateToReference(reference: LeakTraceReference) {
        com.intellij.openapi.application.ReadAction.nonBlocking<OpenFileDescriptor?> {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(reference.owningClassName, GlobalSearchScope.allScope(project))
                ?: return@nonBlocking null

            // Try to find the field
            val field = psiClass.findFieldByName(reference.referenceName, false)
            if (field != null) {
                return@nonBlocking field.containingFile?.virtualFile?.let {
                    OpenFileDescriptor(project, it, field.textOffset)
                }
            }

            // Try to find method
            val method = psiClass.findMethodsByName(reference.referenceName, false).firstOrNull()
            if (method != null) {
                return@nonBlocking method.containingFile?.virtualFile?.let {
                    OpenFileDescriptor(project, it, method.textOffset)
                }
            }

            // Fall back to class navigation
            psiClass.containingFile?.virtualFile?.let {
                OpenFileDescriptor(project, it, psiClass.textOffset)
            }
        }
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { descriptor ->
                descriptor?.navigate(true)
            }.submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
    }

    /**
     * Check if a class exists in the project scope.
     */
    fun isClassInProject(className: String): Boolean {
        return com.intellij.openapi.application.ApplicationManager.getApplication()
            .runReadAction<Boolean> {
            JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.projectScope(project)) != null
        }
    }

    companion object {
        fun getInstance(project: Project): SourceNavigationService =
            project.getService(SourceNavigationService::class.java)
    }
}
