package edu.gemini.spModel.io.impl.migration.to2015A

import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.io.impl.SpIOTags
import edu.gemini.spModel.pio.xml.PioXmlFactory
import edu.gemini.spModel.pio._
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.template.{TemplateParameters, TemplateGroup, TemplateFolder}

import scala.collection.JavaConverters._

// This is not good code.  I am sorry.  If the long-term plan were to keep PIO
// serialization then I think it would be a good idea to make it immutable and
// type-safe and easier to find things by "kind" instead of just name.  As is,
// I don't think it is worth it.
//
// WARNING: This code will mutate the Document in place which is suboptimal but
// we will be turning our back on PIO in favor of JSON serialization anyway ...


/**
 * Conversion of pre-2015A template folder param sets into something readable
 * by 2015A code.
 */
object To2015A {
  private val Version_2015A = Version.`match`("2015A-1")

  private val TemplateFolderName     = TemplateFolder.SP_TYPE.readableStr
  private val TemplateGroupName      = TemplateGroup.SP_TYPE.readableStr
  private val TemplateParametersName = TemplateParameters.SP_TYPE.readableStr

  def updateProgram(d: Document): Unit =
    // Unfortunately we can't find the template folder with a path name
    // because every program has a different name.  :-/
    for {
      pc  <- getContainers(d).find(_.getKind == SpIOTags.PROGRAM)
      if isPre2015A(pc)
      tfc <- Option(pc.getContainer(TemplateFolderName))
    } updateTemplateFolder(tfc)

  def updateTemplateFolder(c: Container): Unit =
    if (isPre2015A(c)) update(c)

  private def isPre2015A(c: Container): Boolean =
    c.getVersion.compareTo(Version_2015A) < 0

  // sorry, PIO is sort of typeless
  private def getContainers(parent: ContainerParent): Iterable[Container] =
    parent.getContainers.asInstanceOf[java.util.List[Container]].asScala

  private def update(c: Container): Unit = {
    val tf = c.getParamSet(TemplateFolderName)

    // Map from key to T (targets or conditions).  We have to potentially
    // add multiple copies of the same target and conditions and they need
    // to be in distinct ParamSet copies.  I don't see another easy way to
    // do this other than conversion from and then back to ParamSet.
    def mapParamSets[T](name: String)(f: ParamSet => T): Map[String, T] =
      (Map.empty[String, T]/:tf.getParamSets(name).asScala) { (m, ps) =>
        val key = Pio.getValue(ps, TemplateFolder.PARAM_MAP_KEY)
        m + (key -> f(ps))
      }

    val targetMap = mapParamSets(SPTarget.PARAM_SET_NAME) { ps =>
      val t = new SPTarget()
      t.setParamSet(ps)
      t
    }

    val condsMap  = mapParamSets(SPSiteQuality.SP_TYPE.readableStr) { ps =>
      val c = new SPSiteQuality()
      c.setParamSet(ps)
      c
    }

    val fact = new PioXmlFactory()

    def getNamedContainers(parent: Container, name: String): Iterable[Container] =
      getContainers(parent).filter(_.getName == name)

    getNamedContainers(c, TemplateGroupName).foreach { tgc =>
      getNamedContainers(tgc, TemplateParametersName).foreach { tpc =>
        val tp       = tpc.getParamSet(TemplateParametersName)

        // Find the args and map the target and conditions to the corresponding
        // objects.
        val args     = tp.getParamSet("templateArgs")
        val targetId = Pio.getValue(args, "target")
        val condsId  = Pio.getValue(args, "siteQuality")
        val time     = Pio.getValue(args, "time")

        // Add them back as children of this template parameters
        // data object ParamSet.
        tp.addParamSet(targetMap(targetId).getParamSet(fact))
        tp.addParamSet(condsMap(condsId).getParamSet(fact))
        Pio.addParam(fact, tp, TemplateParameters.PARAM_TIME, time)
      }
    }
  }
}
