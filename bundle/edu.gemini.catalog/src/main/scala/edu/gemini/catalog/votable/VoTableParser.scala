package edu.gemini.catalog.votable

import java.io.{ByteArrayInputStream, InputStream}

import edu.gemini.spModel.core._
import edu.gemini.spModel.core.Target.SiderealTarget

import scala.io.Source
import scala.xml.XML

import scalaz._
import Scalaz._

object VoTableParser extends VoTableParser {
  type CatalogResult = CatalogProblem \/ ParsedVoResource

  // by band
  private val MagnitudeOrdering: scala.math.Ordering[Magnitude] =
    scala.math.Ordering.by(_.band)

  val UCD_OBJID = Ucd("meta.id;meta.main")
  val UCD_RA = Ucd("pos.eq.ra;meta.main")
  val UCD_DEC = Ucd("pos.eq.dec;meta.main")
  val UCD_PMDEC = Ucd("pos.pm;pos.eq.dec")
  val UCD_PMRA = Ucd("pos.pm;pos.eq.ra")

  val UCD_MAG = UcdWord("phot.mag")
  val STAT_ERR = UcdWord("stat.error")

  val xsd = "/votable-1.2.xsd"
  
  private def validate(xmlFile: InputStream): Throwable \/ String = \/.fromTryCatch {
    import javax.xml.transform.stream.StreamSource
    import javax.xml.validation.SchemaFactory

    val schemaLang = "http://www.w3.org/2001/XMLSchema"
    val factory = SchemaFactory.newInstance(schemaLang)
    val schema = factory.newSchema(new StreamSource(getClass.getResourceAsStream(xsd)))
    val validator = schema.newValidator()

    // Load in memory (Could be a problem for large responses)
    val xmlText = Source.fromInputStream(xmlFile, "UTF-8").getLines().mkString

    validator.validate(new StreamSource(new ByteArrayInputStream(xmlText.getBytes(java.nio.charset.Charset.forName("UTF-8")))))
    xmlText
  }

  /**
   * parse takes an input stream and attempts to read the xml content and convert it to a VoTable resource
   */
  def parse(url: String, is: InputStream): CatalogResult =
    validate(is).fold(k => \/.left(ValidationError(url)), r => \/.right(parse(XML.loadString(r))))
}

// A MagnitudesFilter can ignore fields for certain catalogues and transform others
sealed trait MagnitudesFilter {
  // Indicates if a field should be ignored
  def ignoredMagnitudeField(v: FieldId): Boolean = false
  // Indicates if a magnitude is valid
  def validMagnitude(m: Magnitude): Boolean = !(m.value.isNaN || m.error.exists(_.isNaN))

  // Find what band the field descriptor should represent, in general prefer "upper case" bands over "lower case" Sloan bands.
  // This will prefer U, R and I over u', r' and i' but will map "g" and "z" to the Sloan bands g' and z'.
  def findBand(id: FieldId, band: String): Option[MagnitudeBand] =
    MagnitudeBand.all.
      find(_.name == band.toUpperCase).
      orElse(MagnitudeBand.all.find(_.name == band))

  // filter magnitudes as a whole
  def filterMagnitudeFields(magnitudeFields: List[(FieldId, Magnitude)]): List[Magnitude] = magnitudeFields.collect { case (_, mag) if validMagnitude(mag) => mag }
}

case object UCAC4Filter extends MagnitudesFilter {
  val ucac4BadMagnitude = 20.0
  val ucac4BadMagnitudeError = 0.9.some

  // UCAC4 ignores A-mags
  override def ignoredMagnitudeField(v: FieldId) = v.id === "amag" || v.id === "e_amag"
  // Magnitudes with value 20 or error over or equal to 0.9 are invalid
  override def validMagnitude(m: Magnitude) = super.validMagnitude(m) && m.value =/= ucac4BadMagnitude && m.error.map(math.abs) <= ucac4BadMagnitudeError
  // UCAC4 has a few special cases to map magnitudes, g, r and i refer to the Sloan bands g', r' and i'
  override def findBand(id: FieldId, band: String): Option[MagnitudeBand] = (id.id, id.ucd) match {
    case ("gmag" | "e_gmag", ucd) if ucd.includes(UcdWord("em.opt.r")) => Some(MagnitudeBand._g)
    case ("rmag" | "e_rmag", ucd) if ucd.includes(UcdWord("em.opt.r")) => Some(MagnitudeBand._r)
    case ("imag" | "e_imag", ucd) if ucd.includes(UcdWord("em.opt.i")) => Some(MagnitudeBand._i)
    case _                                                             => super.findBand(id, band)
  }
}

case object PPMXLFilter extends MagnitudesFilter {
  // PPMXL may contain two representations for bands R and B, represented with ids r1mag/r2mag or b1mag/b2mac
  // The ids r1mag/r2mag are preferred but if they are absent we should use the alternative values
  val primaryMagnitudesIds = List("r1mag", "b1mag")
  val alternateMagnitudesIds = List("r2mag", "b2mag")
  val idsMapping = primaryMagnitudesIds.zip(alternateMagnitudesIds)

  override def filterMagnitudeFields(magnitudeFields: List[(FieldId, Magnitude)]): List[Magnitude] = {
    // Read all magnitudes, including duplicates
    val magMap1 = (Map.empty[String, Magnitude]/:magnitudeFields) {
      case (m, (FieldId(i, _), mag)) if validMagnitude(mag) => m + (i -> mag)
      case (m, _)                                           => m
    }
    // Now magMap1 might have double entries for R and B.  Get rid of the alternative if so.
    val magMap2 = (magMap1/:idsMapping) { case (m, (id1, id2)) =>
      if (magMap1.contains(id1)) m - id2 else m
    }
    magMap2.values.toList
  }
}

trait VoTableParser {

  import scala.xml.Node

  private val magRegex = """(?i)em.(opt|IR)(\.\w)?""".r
  private val REQUIRED = List(VoTableParser.UCD_OBJID, VoTableParser.UCD_RA, VoTableParser.UCD_DEC)

  protected def parseFieldDescriptor(xml: Node): Option[FieldDescriptor] = xml match {
    case f @ <FIELD/> =>
      (for {
        id   <- f \ "@ID"
        name <- f \ "@name"
        ucd  <- f \ "@ucd"
      } yield FieldDescriptor(FieldId(id.text, Ucd(ucd.text)), name.text)).headOption
    case _            => None
  }

  protected def parseFields(xml: Node): List[FieldDescriptor] = (for {
      f <- xml \\ "FIELD"
    } yield parseFieldDescriptor(f)).flatten.toList

  protected def parseTableRow(fields: List[FieldDescriptor], xml: Node): TableRow = {
    val rows = for {
      tr <-  xml \\ "TR"
      td =   tr  \  "TD"
      if td.length == fields.length
    } yield for {
        f <- fields.zip(td)
      } yield TableRowItem(f._1, f._2.text)
    TableRow(rows.flatten.toList)
  }

  protected def parseTableRows(fields: List[FieldDescriptor], xml: Node)  =
    for {
      table <-  xml   \\ "TABLEDATA"
      tr    <-  table \\ "TR"
    } yield parseTableRow(fields, tr)

  def parseDoubleValue(ucd: Ucd, s: String): CatalogProblem \/ Double =
    \/.fromTryCatch(s.toDouble).leftMap(_ => FieldValueProblem(ucd, s))
  
  protected def parseBands[T <: MagnitudesFilter](filter: T)(p: (FieldId, String)): CatalogProblem \/ (FieldId, MagnitudeBand, Double) = {
    val (fieldId: FieldId, value: String) = p

    def parseBandToken(token: String):Option[String] = token match {
      case magRegex(_, null) => "UC".some
      case magRegex(_, b)    => b.replace(".", "").some
      case _                 => none
    }

    val band = for {
      t <- fieldId.ucd.tokens
      b <- parseBandToken(t.token)
    } yield filter.findBand(fieldId, b)

    for {
      b <- band.headOption.flatten \/> UnmatchedField(fieldId.ucd)
      v <- parseDoubleValue(fieldId.ucd, value)
    } yield (fieldId, b, v)
  }

  /**
   * Takes an XML Node and attempts to extract the resources and targets from a VOBTable
   */
  protected def parse(xml: Node): ParsedVoResource = {
    val tables = for {
      table <- xml \\ "TABLE"
      fields = parseFields(table)
      tr = parseTableRows(fields, table)
    } yield ParsedTable(tr.map(tableRow2Target(fields)).toList)
    ParsedVoResource(tables.toList)
  }

  /**
   * Convert a table row to a sidereal target or CatalogProblem
   */
  protected def tableRow2Target(fields: List[FieldDescriptor])(row: TableRow): CatalogProblem \/ SiderealTarget = {
    val entries = row.itemsMap
    val entriesByUcd = entries.map(x => x._1.ucd -> x._2)

   val magnitudesFilter: CatalogProblem \/ MagnitudesFilter = {
      val isUCAC4 = fields.exists(_.name === "ucac4")
      val isPPMXL = fields.exists(_.name === "ppmxl")
      if (isUCAC4) UCAC4Filter.right else if (isPPMXL) PPMXLFilter.right else UnknownCatalog.left
    }

    def missing = REQUIRED.filterNot(entriesByUcd.contains)

    def containsMagnitude(v: FieldId, magFilter: MagnitudesFilter) = v.ucd.includes(VoTableParser.UCD_MAG) && v.ucd.matches(magRegex) && !magFilter.ignoredMagnitudeField(v)
    def magnitudeField(v: (FieldId, String), magFilter: MagnitudesFilter) = containsMagnitude(v._1, magFilter) && !v._1.ucd.includes(VoTableParser.STAT_ERR)
    def magnitudeErrorField(v: (FieldId, String), magFilter: MagnitudesFilter) = containsMagnitude(v._1, magFilter) && v._1.ucd.includes(VoTableParser.STAT_ERR)

    def parseProperMotion(pm: (Option[String], Option[String])): CatalogProblem \/ Option[ProperMotion] = {
      val k = for {
        pmra <- pm._1
        pmdec <- pm._2
      } yield for {
          pmrav <- parseDoubleValue(VoTableParser.UCD_PMRA, pmra)
          pmdecv <- parseDoubleValue(VoTableParser.UCD_PMDEC, pmdec)
        } yield ProperMotion(RightAscensionAngularVelocity(AngularVelocity(pmrav)), DeclinationAngularVelocity(AngularVelocity(pmdecv)))

      k.sequenceU
    }

    def combineWithErrorsAndFilter(m: List[(FieldId, MagnitudeBand, Double)], e: List[(FieldId, MagnitudeBand, Double)], magFilter: MagnitudesFilter): List[Magnitude] = {
      val mags = m.map {
          case (f, b, d) => f -> new Magnitude(d, b, b.defaultSystem)
        }
      val magErrors = e.map {
          case (_, b, d) => b -> d
        }.toMap
      // Filter magnitudes as a whole
      val magnitudes = magFilter.filterMagnitudeFields(mags)
      // Link magnitudes with their errors
      magnitudes.map(i => i.copy(error = magErrors.get(i.band))).filter(magFilter.validMagnitude)
    }

    def toSiderealTarget(id: String, ra: String, dec: String, pm: (Option[String], Option[String])): \/[CatalogProblem, SiderealTarget] = {
      for {
        r              <- Angle.parseDegrees(ra).leftMap(_ => FieldValueProblem(VoTableParser.UCD_RA, ra))
        d              <- Angle.parseDegrees(dec).leftMap(_ => FieldValueProblem(VoTableParser.UCD_DEC, dec))
        declination    <- Declination.fromAngle(d) \/> FieldValueProblem(VoTableParser.UCD_DEC, dec)
        properMotion   <- parseProperMotion(pm)
        magFilter      <- magnitudesFilter
        mags            = entries.filter(magnitudeField(_, magFilter))
        magErrs         = entries.filter(magnitudeErrorField(_, magFilter))
        magnitudes     <- mags.map(parseBands(magFilter)).toList.sequenceU
        magnitudeErrs  <- magErrs.map(parseBands(magFilter)).toList.sequenceU
      } yield {
        val coordinates = Coordinates(RightAscension.fromAngle(r), declination)
        val combMags    = combineWithErrorsAndFilter(magnitudes, magnitudeErrs, magFilter).sorted(VoTableParser.MagnitudeOrdering)
        SiderealTarget(id, coordinates, properMotion, combMags, None)
      }
    }

    val result = for {
        id             <- entriesByUcd.get(VoTableParser.UCD_OBJID)
        ra             <- entriesByUcd.get(VoTableParser.UCD_RA)
        dec            <- entriesByUcd.get(VoTableParser.UCD_DEC)
        (pmRa, pmDec)   = (entriesByUcd.get(VoTableParser.UCD_PMRA), entriesByUcd.get(VoTableParser.UCD_PMDEC))
      } yield toSiderealTarget(id, ra, dec, (pmRa, pmDec))

    result.getOrElse(\/.left(MissingValues(missing)))
  }
}
