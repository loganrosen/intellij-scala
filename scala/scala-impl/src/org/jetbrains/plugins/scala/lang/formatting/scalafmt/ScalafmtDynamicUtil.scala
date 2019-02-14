package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadFailure
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.NoopScalafmtReporter
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicDownloader, ScalafmtReflect}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

// TODO: somehow preserve resolved scalafmt cache between intellij restarts
object ScalafmtDynamicUtil {
  type Version = String

  private val formattersCache: mutable.Map[Version, ResolveStatus] = ScalaCollectionsUtil.newConcurrentMap

  def isAvailable(version: Version): Boolean = ???

  // TODO: instead of returning download in progress error maybe we can somehow reuse already downloading
  //  process and use it's result? We need some abstraction, like Task or something like that
  // TODO: set project in dummy state?
  def resolve(version: Version, downloadIfMissing: Boolean = false): Either[ScalafmtResolveError, ScalafmtReflect] = {
    val resolveResult = formattersCache.get(version) match {
      case Some(ResolveStatus.Downloaded(scalaFmt)) => Right(scalaFmt)
      case Some(ResolveStatus.DownloadInProgress) => Left(ScalafmtResolveError.DownloadInProgress(version))
      case _ if !downloadIfMissing => Left(ScalafmtResolveError.NotFound(version))
      case _ =>
        download(version) match {
          case Right(scalaFmt) =>
            formattersCache(version) = ResolveStatus.Downloaded(scalaFmt)
            Right(scalaFmt)
          case Left(failure: DownloadFailure) =>
            Left(ScalafmtResolveError.DownloadError(failure))
        }
    }
    resolveResult.left.foreach(reportResolveError)
    resolveResult
  }

  private def reportResolveError(error: ScalafmtResolveError): Unit = {
    import ScalafmtNotifications._

    val commonMessage = s"Can not resolve scalafmt version `${error.version}`"
    error match {
      case ScalafmtResolveError.DownloadInProgress(_) =>
        val errorMessage = s"$commonMessage: download is in progress"
        displayError(errorMessage)
      case ScalafmtResolveError.DownloadError(failure) =>
        val causeMessage = failure.cause.map(_.getMessage).getOrElse("<unknown reason>")
        val errorMessage = s"$commonMessage: an error occurred during downloading:\n$causeMessage"
        displayError(errorMessage)
      case ScalafmtResolveError.NotFound(_) =>
        val message =
          s"Scalafmt version `${error.version}` is not downloaded yet. " +
            s"Would you like to to download it?"
        displayWarning(message, Seq(new DownloadScalafmtNotificationActon(error.version)))
    }
  }

  private class DownloadScalafmtNotificationActon(version: String) extends NotificationAction("download") {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      resolveAsync(version, e.getProject, onDownloadFinished = {
        case Right(_) => ScalafmtNotifications.displayInfo(s"Scalafmt version $version was downloaded")
        case _ => // relying on error reporting in resolve method
      })
      notification.expire()
    }
  }

  private def resolveAsync(version: Version, project: ProjectContext,
                           onDownloadFinished: Either[ScalafmtResolveError, ScalafmtReflect] => Unit): Unit = {
    val backgroundTask = new Task.Backgroundable(project, s"Downloading scalafmt (version `$version`)", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setFraction(0.0)
        val result = resolve(version, downloadIfMissing = true)
        onDownloadFinished(result)
        indicator.setFraction(1.0)
      }
    }
    ProgressManager.getInstance.run(backgroundTask)
  }

  private def download(version: Version): Either[DownloadFailure, ScalafmtReflect] = {
    // TODO: 1) use some proper reporter ScalafmtReporter?
    //       2) handle timeout ?
    val ttl = 1.hour
    val downloader = new ScalafmtDynamicDownloader(
      respectVersion = true,
      new NoopScalafmtReporter,
      ttl = Some(ttl)
    )
    downloader.download(version)
  }

  private sealed trait ResolveStatus
  private object ResolveStatus {
    object DownloadInProgress extends ResolveStatus
    case class Downloaded(instance: ScalafmtReflect) extends ResolveStatus
  }

  sealed trait ScalafmtResolveError {
    def version: Version
  }

  object ScalafmtResolveError {
    case class NotFound(version: Version) extends ScalafmtResolveError
    case class DownloadInProgress(version: Version) extends ScalafmtResolveError
    case class DownloadError(failure: DownloadFailure) extends ScalafmtResolveError {
      override def version: Version = failure.version
    }
  }
}