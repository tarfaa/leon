package purescala

import z3.scala._
import Common._
import Definitions._
import Extensions._
import Trees._
import TypeTrees._

trait Z3ModelReconstruction {
  self: Z3Solver =>

  def modelValue(model: Z3Model, id: Identifier, tpe: TypeTree = null) : Option[Expr] = {
    val expectedType = if(tpe == null) id.getType else tpe
    
    if(!exprToZ3Id.isDefinedAt(id.toVariable)) None else {
      val z3ID : Z3AST = exprToZ3Id(id.toVariable)

      expectedType match {
        case BooleanType => model.evalAs[Boolean](z3ID).map(BooleanLiteral(_))
        case Int32Type => model.evalAs[Int](z3ID).map(IntLiteral(_))
        case other => model.eval(z3ID) match {
          case None => None
          case Some(t) => softFromZ3Formula(t)
        }
      }
    }
  }

  def printExtractedModel(model: Z3Model, ids : Iterable[Identifier]) : Unit = {
    reporter.info("Tentative extracted model")
    reporter.info("*************************")
    for(id <- ids) {
      val strRep = modelValue(model, id) match {
        case Some(e) => e.toString
        case None => "??"
      }
      reporter.info(id + "       ->     " + strRep)
    }
  }
}