package edu.gemini.itc.baseline.util

import edu.gemini.itc.shared.GnirsParameters
import edu.gemini.itc.michelle.MichelleParameters
import edu.gemini.itc.nifs.NifsParameters
import edu.gemini.itc.shared._
import edu.gemini.itc.trecs.TRecsParameters
import edu.gemini.spModel.target._

// TEMPORARY helper
// All input objects will become immutable data only objects (probably Scala case classes).
// For now we need a workaround for missing hash functions on the existing Java objects.
object Hash {

  def calc(ip: InstrumentDetails): Int = ip match {
    case p: AcquisitionCamParameters  => calc(p)
    case p: Flamingos2Parameters      => calc(p)
    case p: GmosParameters            => calc(p)
    case p: GnirsParameters           => calc(p)
    case p: GsaoiParameters           => calc(p)
    case p: MichelleParameters        => calc(p)
    case p: NifsParameters            => calc(p)
    case p: NiriParameters            => calc(p)
    case p: TRecsParameters           => calc(p)
    case _                            => throw new Exception("no hash function available")
  }

  def calc(p: GmosParameters): Int =
    hash(
      p.filter.name,
      p.ccdType.name,
      p.fpMask.name,
      p.grating.name,
      p.ifuMethod.toString,
      p.centralWavelength,
      p.site.name,
      p.spatialBinning,
      p.spectralBinning
    )

  def calc(p: GnirsParameters): Int =
    hash(
      p.grating.name,
      p.pixelScale.name,
      p.crossDispersed.name,
      p.readMode.name,
      p.centralWavelength.toNanometers,
      p.slitWidth.name
    )

  def calc(p: GsaoiParameters): Int =
    hash(
      p.filter.name,
      p.readMode.name,
      calc(p.gems)
    )

  def calc(p: MichelleParameters): Int =
    hash(
      p.getFilter,
      p.getFocalPlaneMask.name,
      p.getGrating,
      p.getInstrumentCentralWavelength,
      p.polarimetryIsUsed()
    )

  def calc(p: NifsParameters): Int =
    hash(
      p.getFilter.name,
      p.getGrating.name,
      p.getReadMode.name,
      p.getInstrumentCentralWavelength,
      p.getIFUMethod,
      calc(p.getAltair)
    )

  def calc(p: NiriParameters): Int =
    hash(
      p.camera.name,
      p.filter.name,
      p.mask.name,
      p.grism.name,
      p.readMode.name,
      p.wellDepth.name,
      calc(p.altair)
    )

  def calc(p: TRecsParameters): Int =
    hash(
      p.getFilter,
      p.getFocalPlaneMask,
      p.getGrating,
      p.getInstrumentCentralWavelength,
      p.getInstrumentWindow
    )

  def calc(p: AcquisitionCamParameters): Int =
    hash(
      p.colorFilter.name,
      p.ndFilter.name
    )

  def calc(p: Flamingos2Parameters): Int =
    hash(
      p.filter.name,
      p.mask.name,
      p.grism.name,
      p.readMode.name
    )

  def calc(odp: ObservationDetails): Int =
    hash(
      odp.isAutoAperture,
      odp.getMethod.isS2N,
      odp.getMethod.isImaging,
      odp.getExposureTime,
      odp.getNumExposures,
      odp.getApertureDiameter,
      odp.getSkyApertureDiameter,
      odp.getSNRatio,
      odp.getSourceFraction
    )

  def calc(src: SourceDefinition): Int =
    hash(
      src.getProfileType.name,
      src.getDistributionType.name,
      src.profile,
      src.distribution,
      src.norm,               // this is the magnitude value
      src.normBand.name,      // this is the magnitude band name
      src.redshift
    )

  def calc(tp: TelescopeDetails): Int =
    hash(
      tp.getInstrumentPort.displayValue,
      tp.getMirrorCoating.displayValue,
      tp.getWFS.name.toLowerCase
    )

  def calc(ocp: ObservingConditions): Int =
    hash(
      ocp.getAirmass,
      ocp.getImageQuality,
      ocp.getSkyTransparencyCloud,
      ocp.getSkyTransparencyWater,
      ocp.getSkyBackground
    )

  def calc(alt: Option[AltairParameters]): Int = alt match {
    case Some(altair) => calc(altair)
    case None         => 0
  }

  def calc(alt: AltairParameters): Int =
    hash (
      alt.guideStarMagnitude,
      alt.guideStarSeparation,
      alt.fieldLens.name,
      alt.wfsMode.name
    )

  def calc(alt: GemsParameters): Int =
    hash(
      alt.avgStrehl,
      alt.strehlBand
    )

  def calc(pdp: PlottingDetails): Int =
    hash(pdp.getPlotWaveL, pdp.getPlotWaveU)

  private def hash(values: Any*) =
    values.
      filter(_ != null).
      map(_.hashCode).
      foldLeft(17)((acc, h) => 37*acc + h)

}

