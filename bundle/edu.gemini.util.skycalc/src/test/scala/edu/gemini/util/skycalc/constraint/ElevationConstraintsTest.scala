package edu.gemini.util.skycalc.constraint

import edu.gemini.skycalc.TimeUtils
import edu.gemini.spModel.core.Site
import edu.gemini.util.skycalc.SiderealTarget
import edu.gemini.util.skycalc.calc.{Solution, Interval, TargetCalculator}
import jsky.coords.WorldCoords
import org.junit.Assert._
import org.junit.{Ignore, Test}

/**
 * Check elevation constraints for some examples.
 */
class ElevationConstraintsTest {

  @Test def checkHourAngleConstraint(): Unit = {

    val start = TimeUtils.time(2014, 12, 4, 17, 30, Site.GN.timezone())
    val end = TimeUtils.time(2014, 12, 5, 6, 30, Site.GN.timezone())
    val pos = new WorldCoords("17:32:10.569", "+55:11:03.27")
    val target = SiderealTarget(pos)
    val range = Interval(start, end)
    val calc = TargetCalculator(Site.GN, target, range)
    val constraint = HourAngleConstraint(-2, +2)
    val solution = constraint.solve(range, calc)

    assertEquals(Solution.Never, solution)

  }

}
