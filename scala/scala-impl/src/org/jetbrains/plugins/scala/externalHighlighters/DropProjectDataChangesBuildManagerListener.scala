package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.{BuildManager, BuildManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

/**
 * Issue: SCL-17303
 *
 * Drop changes of [[com.intellij.compiler.server.BuildManager.ProjectData]] to fix the problem.
 */
class DropProjectDataChangesBuildManagerListener
  extends BuildManagerListener {

  override def beforeBuildProcessStarted(project: Project, sessionId: UUID): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      BuildManager.getInstance.clearState(project)
}
