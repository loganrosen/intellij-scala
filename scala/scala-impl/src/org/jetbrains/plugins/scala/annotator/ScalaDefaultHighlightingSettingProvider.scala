package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.{DefaultHighlightingSettingProvider, FileHighlightingSetting}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiJavaFile, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

class ScalaDefaultHighlightingSettingProvider
  extends DefaultHighlightingSettingProvider {

  override def getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting =
    Option(PsiManager.getInstance(project).findFile(file))
      .filter(_.isInScala3Module)
      .collect {
        case _: ScalaFile => FileHighlightingSetting.SKIP_INSPECTION
        case _: PsiJavaFile => FileHighlightingSetting.SKIP_HIGHLIGHTING
      }.orNull
}
