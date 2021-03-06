/* Copyright 2009-2013 EPFL, Lausanne */

package leon.xlang

import leon.TransformationPhase
import leon.LeonContext
import leon.purescala.Common._
import leon.purescala.Definitions._
import leon.purescala.Trees._
import leon.purescala.TreeOps._
import leon.purescala.TypeTrees._
import leon.xlang.Trees._

object EpsilonElimination extends TransformationPhase {

  val name = "Epsilon Elimination"
  val description = "Remove all epsilons from the program"

  def apply(ctx: LeonContext, pgm: Program): Program = {

    val allFuns = pgm.definedFunctions
    allFuns.foreach(fd => fd.body.map(body => {
      val newBody = postMap{
        case eps@Epsilon(pred) =>
          val freshName = FreshIdentifier("epsilon")
          val newFunDef = new FunDef(freshName, Nil, eps.getType, Seq())
          val epsilonVar = EpsilonVariable(eps.getPos)
          val resId     = FreshIdentifier("res").setType(eps.getType)
          val postcondition = replace(Map(epsilonVar -> Variable(resId)), pred)
          newFunDef.postcondition = Some((resId, postcondition))
          Some(LetDef(newFunDef, FunctionInvocation(newFunDef.typed, Seq())))

        case _ =>
          None
      }(body)
      fd.body = Some(newBody)
    }))
    pgm
  }

}
