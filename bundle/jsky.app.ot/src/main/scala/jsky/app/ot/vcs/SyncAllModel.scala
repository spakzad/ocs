package jsky.app.ot.vcs

import edu.gemini.pot.sp.ISPProgram
import edu.gemini.pot.sp.version.VersionMap
import edu.gemini.shared.util.VersionComparison
import edu.gemini.shared.util.VersionComparison.Same
import edu.gemini.sp.vcs2.VcsFailure
import edu.gemini.spModel.core.SPProgramID

object SyncAllModel {
  sealed trait State {
    def isPending: Boolean = false
    def isTerminal: Boolean = false
  }

  object State {
    case object ProgramStatusUpdating extends State {
      def setVersionComparison(vc: Option[VersionComparison]): State =
        vc match {
          case Some(Same)  => InSync
          case None        => SyncFailed(None)
          case Some(other) => PendingSync(other)
        }
    }

    case class PendingSync(vc: VersionComparison) extends State {
      override def isPending: Boolean = true
    }

    case class SyncInProgress(vc: VersionComparison) extends State

    case class SyncFailed(t: Option[VcsFailure]) extends State {
      override def isTerminal: Boolean = true
    }

    case object InSync extends State {
      override def isTerminal: Boolean = true
    }

    case object Conflicts extends State {
      override def isTerminal: Boolean = true
    }

    def init(sp: ISPProgram): State =
      if (ConflictNavigator.hasConflicts(sp)) Conflicts
      else ProgramStatusUpdating
  }

  case class ProgramSync(program: ISPProgram, state: State) {
    val pid: SPProgramID = program.getProgramID
  }

  def init(progs: Vector[ISPProgram]): SyncAllModel =
    SyncAllModel(progs.filter(p => Option(p.getProgramID).isDefined).map { p =>
      ProgramSync(p, State.init(p))
    }.sortBy(_.pid))

  val empty = SyncAllModel(Vector.empty[ProgramSync])
}

import SyncAllModel._

case class SyncAllModel(programs: Vector[ProgramSync]) {
  lazy val syncEnabled: Boolean =
    programs.forall(_.state != State.ProgramStatusUpdating) && programs.exists(_.state.isPending)

  private def search(f: Vector[ProgramSync] => ((ProgramSync => Boolean) => Boolean), pf: PartialFunction[State, Boolean]): Boolean =
    f(programs)(ps => if (pf.isDefinedAt(ps.state)) pf(ps.state) else false)

  def existsState(pf: PartialFunction[State, Boolean]): Boolean = search(_.exists, pf)
  def forallState(pf: PartialFunction[State, Boolean]): Boolean = search(_.forall, pf)

  private def indexOf(pid: SPProgramID): Int =
    programs.indexWhere(_.pid == pid)

  private def update(pid: SPProgramID)(f: ProgramSync => ProgramSync): SyncAllModel =
    indexOf(pid) match {
      case -1 => this
      case  n => SyncAllModel(programs.updated(n, f(programs(n))))
    }

  private def updateState(pid: SPProgramID)(f: ProgramSync => State): SyncAllModel =
    update(pid) { ss => ss.copy(state = f(ss)) }

  def updateRemoteVersion(pid: SPProgramID, remoteVm: VersionMap): SyncAllModel =
    updateState(pid) { ss =>
      ss.state match {
        case State.ProgramStatusUpdating =>
          State.ProgramStatusUpdating.setVersionComparison(Some(VersionMap.compare(ss.program.getVersions, remoteVm)))
        case _ => ss.state
      }
    }

  def markSyncInProgress: SyncAllModel =
    SyncAllModel(programs.map { ss =>
      ss.state match {
        case State.PendingSync(ps) => ss.copy(state = State.SyncInProgress(ps))
        case _                     => ss
      }
    })

  def markSyncFailed(pid: SPProgramID, t: Option[VcsFailure]): SyncAllModel =
    updateState(pid) { _ => State.SyncFailed(t) }

  def markSyncConflict(pid: SPProgramID): SyncAllModel =
    updateState(pid) { _ => State.Conflicts }

  def markSuccess(pid: SPProgramID): SyncAllModel =
    updateState(pid) { _ => State.InSync }
}
