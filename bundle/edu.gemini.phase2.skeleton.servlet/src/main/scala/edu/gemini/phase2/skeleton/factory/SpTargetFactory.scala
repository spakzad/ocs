package edu.gemini.phase2.skeleton.factory


import edu.gemini.model.p1.immutable._
import edu.gemini.model.p1.mutable.CoordinatesEpoch._
import edu.gemini.shared.{skyobject => SO}
import edu.gemini.shared.util.immutable.DefaultImList
import edu.gemini.spModel.{target => SP}
import edu.gemini.spModel.target.system.CoordinateTypes.{PM1 => SPProperMotionRA}
import edu.gemini.spModel.target.system.CoordinateTypes.{PM2 => SPProperMotionDec}

import java.text.DateFormat
import scala.collection.JavaConverters._

object SpTargetFactory {

  def create(t: Target, time: Long): Either[String, SP.SPTarget] =
    t match {
      case too: TooTarget         => Right(createTooTarget(too))
      case nsd: NonSiderealTarget => createNonSiderealTarget(nsd, time)
      case sid: SiderealTarget    => createSiderealTarget(sid, time)
    }

  private def createTooTarget(too: TooTarget): SP.SPTarget = {
    val sp   = new SP.SPTarget(0.0, 0.0)
    sp.setName(too.name)
    sp
  }

  private def siderealCoordinates(t: Target, time: Long): Either[String, Coordinates] =
    t.coords(time).toRight("Target coordinates could not be determined on %s".format(formatTime(time)))

  private def nonSiderealCoordinates(nsid: NonSiderealTarget, time: Long): Either[String, (Coordinates, Long)] =
    closestCoordinates(nsid, time).toRight("Non-sidreal target coordinates missing ephemeris data.")

  private def closestCoordinates(nsid: NonSiderealTarget, time: Long): Option[(Coordinates, Long)] =
    closestEphemeris(nsid, time) map { e => (e.coords, e.validAt) }

  private def closestEphemeris(nsid: NonSiderealTarget, time: Long): Option[EphemerisElement] =
    nsid.ephemeris match {
      case Nil => None
      case lst => Some(lst minBy { e => (time - e.validAt).abs })
    }

  private def formatTime(time: Long): String =
    DateFormat.getDateTimeInstance.format(new java.util.Date(time))


  private def createNonSiderealTarget(nsid: NonSiderealTarget, time: Long): Either[String, SP.SPTarget] =
    for {
      coordsAt <- nonSiderealCoordinates(nsid, time).right
    } yield {
      val itarget = new SP.system.ConicTarget()
      val (coords, when) = coordsAt
      setRaDec(itarget, coords)
      itarget.setDateForPosition(new java.util.Date(when))

      val spTarget = new SP.SPTarget(itarget)
      spTarget.setName(nsid.name)
      nsid.magnitude(time) foreach { m => spTarget.setBrightness(m.toString) }
      spTarget
    }


  private def createSiderealTarget(sid: SiderealTarget, time: Long): Either[String, SP.SPTarget] =
    for {
      coords <- siderealCoordinates(sid, time).right
      system <- siderealSystemType(sid).right
      mags   <- siderealMags(sid).right
    } yield {
      val itarget  = new SP.system.HmsDegTarget(system)
      setRaDec(itarget, coords)
      sid.properMotion map { pm =>
        val ra  = pm.deltaRA
        val dec = pm.deltaDec
        itarget.setPM1(new SPProperMotionRA(ra,   SP.system.CoordinateParam.Units.MILLI_ARCSECS_PER_YEAR))
        itarget.setPM2(new SPProperMotionDec(dec, SP.system.CoordinateParam.Units.MILLI_ARCSECS_PER_YEAR))
      }

      val spTarget = new SP.SPTarget(itarget)
      spTarget.setName(sid.name)
      spTarget.setMagnitudes(DefaultImList.create(mags.asJava))
      spTarget
    }

  private def setRaDec(itarget: SP.system.ITarget, c: Coordinates) {
    val degDeg   = c.toDegDeg
    itarget.setC1(new SP.system.HMS(degDeg.ra.toDouble))
    itarget.setC2(new SP.system.DMS(degDeg.dec.toDouble))
  }

  private def siderealSystemType(sid: SiderealTarget): Either[String, SP.system.HmsDegTarget.SystemType] =
    sid.epoch match {
      case J_2000 => Right(SP.system.HmsDegTarget.SystemType.J2000)
      case B_1950 => Right(SP.system.HmsDegTarget.SystemType.B1950)
      case _      => Left("Unexpected epoch %s".format(sid.epoch))
    }

  private def siderealMags(sid: SiderealTarget): Either[String, List[SO.Magnitude]] = {
    val empty: Either[String, List[SO.Magnitude]] = Right(Nil)
    (empty/:sid.magnitudes) {
      (e, m) => e.right flatMap { lst =>
        mag(m).right map { _ :: lst }
      }
    }
  }

  private def mag(m: Magnitude): Either[String, SO.Magnitude] =
    for {
      band   <- band(m.band).right
      system <- system(m.system).right
    } yield new SO.Magnitude(band, m.value, system)

  // Find the magnitude band with the same name.
  private def band(b: MagnitudeBand): Either[String, SO.Magnitude.Band] = {
    val opt = SO.Magnitude.Band.values() find { _.name() == b.name() }
    opt.toRight("Unexpected magnitude band: %s".format(b.name()))
  }

  private def system(s: MagnitudeSystem): Either[String, SO.Magnitude.System] = {
    val opt = SO.Magnitude.System.values() find { _.name().equalsIgnoreCase(s.name()) }
    opt.toRight("Unexpected magnitude system: %s".format(s.name()))
  }
}