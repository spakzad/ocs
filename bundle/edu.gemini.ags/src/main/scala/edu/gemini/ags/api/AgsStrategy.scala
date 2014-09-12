package edu.gemini.ags.api

import edu.gemini.ags.api.AgsMagnitude.{MagnitudeCalc, MagnitudeTable}
import edu.gemini.catalog.api.QueryConstraint
import edu.gemini.shared.skyobject.SkyObject
import edu.gemini.skycalc.Angle
import edu.gemini.spModel.ags.AgsStrategyKey
import edu.gemini.spModel.guide.GuideProbe
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.{OptionsList, GuideProbeTargets, TargetEnvironment}

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait AgsStrategy {
  def key: AgsStrategyKey

  def magnitudes(ctx: ObsContext, mt: MagnitudeTable): List[(GuideProbe, MagnitudeCalc)]

  def analyze(ctx: ObsContext, mt: MagnitudeTable): List[AgsAnalysis]

  def candidates(ctx: ObsContext, mt: MagnitudeTable): Future[List[(GuideProbe, List[SkyObject])]]

  def estimate(ctx: ObsContext, mt: MagnitudeTable): Future[AgsStrategy.Estimate]

  def select(ctx: ObsContext, mt: MagnitudeTable): Future[Option[AgsStrategy.Selection]]

  def queryConstraints(ctx: ObsContext, mt: MagnitudeTable): List[QueryConstraint]

  def guideProbes: List[GuideProbe]
}

object AgsStrategy {
  object Estimate {
    val CompleteFailure   = Estimate(0.0)
    val GuaranteedSuccess = Estimate(1.0)

    def toEstimate(probability: Double): Estimate =
      Estimate(probability).normalize
  }

  /**
   * Estimation of success of finding a guide star at phase 2 time.
   */
  case class Estimate(probability: Double) extends AnyVal {
    def normalize: Estimate =
      if (probability <= 0) Estimate.CompleteFailure
      else if (probability >= 1) Estimate.GuaranteedSuccess
      else this
  }

  /**
   * An assignment of a guide star to a particular guide probe.
   */
  case class Assignment(guideProbe: GuideProbe, guideStar: SkyObject)

  /**
   * Results of running an AGS selection.  The position angle for which the
   * results are valid along with all assignments of guide probes to stars.
   */
  case class Selection(posAngle: Angle, assignments: List[Assignment]) {

    /**
     * Creates a new TargetEnvironment with guide stars for each assignment in
     * the Selection.
     */
    def applyTo(env: TargetEnvironment): TargetEnvironment = {
      def findMatching(gpt: GuideProbeTargets, target: SPTarget): Option[SPTarget] =
        Option(target.getName).flatMap { n =>
          gpt.getOptions.toList.asScala.find { t =>
            Option(t.getName).map(_.trim).exists(_ == n.trim)
          }
        }

      (env/:assignments) { (curEnv, ass) =>
        val target = new SPTarget(ass.guideStar)
        val oldGpt = curEnv.getPrimaryGuideProbeTargets(ass.guideProbe).asScalaOpt

        val newGpt = oldGpt.fold(GuideProbeTargets.create(ass.guideProbe, target)) { gpt =>
          // We already have guide probe targets for guide probe.
          // Does one with the same target name already exist? If so, mark it as
          // primary and replace it with the target we just made.  If not, add the
          // target and mark it as primary.
          findMatching(gpt, target).fold(gpt.update(OptionsList.UpdateOps.appendAsPrimary(target))) { existing =>
            gpt.selectPrimary(existing).setPrimary(target)
          }
        }

        curEnv.putPrimaryGuideProbeTargets(newGpt)
      }
    }
  }
}