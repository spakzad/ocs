package edu.gemini.spModel.core

import scalaz._
import Scalaz._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck.Gen
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import AlmostEqual.AlmostEqualOps

object AngleSpec extends Specification with ScalaCheck with Arbitraries {

  "Angle Conversions" should {
   
    "support Degrees" !
      forAll { (a: Angle) =>  
        Angle.fromDegrees(a.toDegrees) ~= a
      }

    "support Radians" !
      forAll { (a: Angle) =>  
        Angle.fromRadians(a.toRadians) ~= a
      }

    "support HourAngle" !
      forAll { (a: Angle) => 
        val hms = a.toHourAngle
        Angle.fromHourAngle(hms.hours, hms.minutes, hms.seconds).get ~= a
      }

    "support Sexigesimal" !
      forAll { (a: Angle) => 
        val dms = a.toSexigesimal
        Angle.fromSexigesimal(dms.degrees, dms.minutes, dms.seconds).get ~= a
      }

  }


  "Angle Scalar Multplication" should {

    "have identity" !
      forAll { (a: Angle) =>  
        a * 1 ~= a
      }

    "have void" ! 
      forAll { (a: Angle) =>
        a * 0 ~= Angle.zero
      }

    "be consistent with addition" ! 
      forAll { (a: Angle) =>
        a * 2 ~= a + a
      }

  }

  "Angle Addition" should {

    "have left identity" !
      forAll { (a: Angle) =>  
        Angle.zero + a ~= a
      }

    "have right identity" !
      forAll { (a: Angle) =>  
        a + Angle.zero ~= a
      }

    "commute" ! 
      forAll { (a: Angle, b: Angle) =>
        a + b ~= b + a
      }

    "associate" ! 
      forAll { (a: Angle, b: Angle, c: Angle) =>
        a + (b + c) ~= (a + b) + c
      }

    "be equivalent to subtraction when negated" ! 
      forAll { (a: Angle, b: Angle) =>
        a + b ~= a - (b * -1)
      }

  }

  "Angle Subtraction" should {

    "have right identity" !
      forAll { (a: Angle) =>  
        a - Angle.zero ~= a
      }

    "be equivalent to addition when negated" ! 
      forAll { (a: Angle, b: Angle) =>
        a - b ~= a + (b * -1)
      }

  }


}

