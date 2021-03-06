/* Copyright 2009-2013 EPFL, Lausanne */

package leon
package verification

import purescala.Common._
import purescala.Trees._
import purescala.TreeOps._
import purescala.Extractors._
import purescala.Definitions._

import scala.collection.mutable.{Map => MutableMap}

class DefaultTactic(reporter: Reporter) extends Tactic(reporter) {
    val description = "Default verification condition generation approach"
    override val shortDescription = "default"

    var _prog : Option[Program] = None
    def program : Program = _prog match {
      case None => throw new Exception("Program never set in DefaultTactic.")
      case Some(p) => p
    }

    override def setProgram(program: Program) : Unit = {
      _prog = Some(program)
    }

    def generatePostconditions(functionDefinition: FunDef) : Seq[VerificationCondition] = {
      assert(functionDefinition.body.isDefined)
      val prec = functionDefinition.precondition
      val optPost = functionDefinition.postcondition
      val body = matchToIfThenElse(functionDefinition.body.get)

      optPost match {
        case None =>
          Seq()

        case Some((id, post)) =>
          val theExpr = { 
            val resFresh = FreshIdentifier("result", true).setType(body.getType)
            val bodyAndPost = Let(resFresh, body, replace(Map(Variable(id) -> Variable(resFresh)), matchToIfThenElse(post)))

            val withPrec = if(prec.isEmpty) {
              bodyAndPost
            } else {
              Implies(matchToIfThenElse(prec.get), bodyAndPost)
            }

            withPrec
          }
          Seq(new VerificationCondition(theExpr, functionDefinition, VCKind.Postcondition, this).setPos(post))
      }
    }
  
    def generatePreconditions(function: FunDef) : Seq[VerificationCondition] = {
      val toRet = if(function.hasBody) {
        val pre = matchToIfThenElse(function.body.get)
        val cleanBody = expandLets(pre)

        val allPathConds = collectWithPathCondition((t => t match {
          case FunctionInvocation(tfd, _) if(tfd.hasPrecondition) => true
          case _ => false
        }), cleanBody)

        def withPrecIfDefined(path: Seq[Expr], shouldHold: Expr) : Expr = if(function.hasPrecondition) {
          Not(And(And(matchToIfThenElse(function.precondition.get) +: path), Not(shouldHold)))
        } else {
          Not(And(And(path), Not(shouldHold)))
        }

        allPathConds.map(pc => {
          val path : Seq[Expr] = pc._1
          val fi = pc._2.asInstanceOf[FunctionInvocation]
          val FunctionInvocation(tfd, args) = fi
          val prec : Expr = freshenLocals(matchToIfThenElse(tfd.precondition.get))
          val newLetIDs = tfd.params.map(a => FreshIdentifier("arg_" + a.id.name, true).setType(a.tpe))
          val substMap = Map[Expr,Expr]((tfd.params.map(_.toVariable) zip newLetIDs.map(Variable(_))) : _*)
          val newBody : Expr = replace(substMap, prec)
          val newCall : Expr = (newLetIDs zip args).foldRight(newBody)((iap, e) => Let(iap._1, iap._2, e))

            new VerificationCondition(
              withPrecIfDefined(path, newCall),
              function,
              VCKind.Precondition,
              this.asInstanceOf[DefaultTactic]).setPos(fi)
        }).toSeq
      } else {
        Seq.empty
      }

      // println("PRECS VCs FOR " + function.id.name)
      // println(toRet.toList.map(vc => vc.posInfo + " -- " + vc.condition).mkString("\n\n"))

      toRet
    }

    def generatePatternMatchingExhaustivenessChecks(function: FunDef) : Seq[VerificationCondition] = {
      val toRet = if(function.hasBody) {
        val cleanBody = matchToIfThenElse(function.body.get)

        val allPathConds = collectWithPathCondition((t => t match {
          case Error("non-exhaustive match") => true
          case _ => false
        }), cleanBody)
  
        def withPrecIfDefined(conds: Seq[Expr]) : Expr = if(function.hasPrecondition) {
          Not(And(matchToIfThenElse(function.precondition.get), And(conds)))
        } else {
          Not(And(conds))
        }

        allPathConds.map(pc => 
          new VerificationCondition(
            withPrecIfDefined(pc._1),
            function,//if(function.fromLoop) function.parent.get else function,
            VCKind.ExhaustiveMatch,
            this.asInstanceOf[DefaultTactic]).setPos(pc._2)
        ).toSeq
      } else {
        Seq.empty
      }

      // println("MATCHING VCs FOR " + function.id.name)
      // println(toRet.toList.map(vc => vc.posInfo + " -- " + vc.condition).mkString("\n\n"))

      toRet
    }

    def generateMapAccessChecks(function: FunDef) : Seq[VerificationCondition] = {
      val toRet = if (function.hasBody) {
        val cleanBody = mapGetWithChecks(matchToIfThenElse(function.body.get))

        val allPathConds = collectWithPathCondition((t => t match {
          case Error("key not found for map access") => true
          case _ => false
        }), cleanBody)

        def withPrecIfDefined(conds: Seq[Expr]) : Expr = if (function.hasPrecondition) {
          Not(And(mapGetWithChecks(matchToIfThenElse(function.precondition.get)), And(conds)))
        } else {
          Not(And(conds))
        }

        allPathConds.map(pc =>
          new VerificationCondition(
            withPrecIfDefined(pc._1),
            function, //if(function.fromLoop) function.parent.get else function,
            VCKind.MapAccess,
            this.asInstanceOf[DefaultTactic]).setPos(pc._2)
        ).toSeq
      } else {
        Seq.empty
      }

      toRet
    }

    def generateArrayAccessChecks(function: FunDef) : Seq[VerificationCondition] = {
      val toRet = if (function.hasBody) {
        val cleanBody = matchToIfThenElse(function.body.get)

        val allPathConds = new CollectorWithPaths({
          case expr@ArraySelect(a, i) => (expr, a, i)
          case expr@ArrayUpdated(a, i, _) => (expr, a, i)
        }).traverse(cleanBody)

        val arrayAccessConditions = allPathConds.map{
          case ((expr, array, index), pathCond) => {
            val length = ArrayLength(array)
            val negative = LessThan(index, IntLiteral(0))
            val tooBig = GreaterEquals(index, length)
            (And(pathCond, Or(negative, tooBig)), expr)
          }
        }

        def withPrecIfDefined(conds: Expr) : Expr = if (function.hasPrecondition) {
          Not(And(mapGetWithChecks(matchToIfThenElse(function.precondition.get)), conds))
        } else {
          Not(conds)
        }


        arrayAccessConditions.map(pc =>
          new VerificationCondition(
            withPrecIfDefined(pc._1),
            function, //if(function.fromLoop) function.parent.get else function,
            VCKind.ArrayAccess,
            this.asInstanceOf[DefaultTactic]).setPos(pc._2)
        ).toSeq
      } else {
        Seq.empty
      }

      toRet
    }

    def generateMiscCorrectnessConditions(function: FunDef) : Seq[VerificationCondition] = {
      generateMapAccessChecks(function)
    }

    def collectWithPathCondition(matcher: Expr=>Boolean, expression: Expr) : Set[(Seq[Expr],Expr)] = {
      new CollectorWithPaths({ 
        case e if matcher(e) => e 
      }).traverse(expression).map{ 
        case (e, And(es)) => (es, e)
        case (e1, e2) => (Seq(e2), e1)
      }.toSet
    }
    // prec: there should be no lets and no pattern-matching in this expression
    //def collectWithPathCondition(matcher: Expr=>Boolean, expression: Expr) : Set[(Seq[Expr],Expr)] = {
    //  var collected : Set[(Seq[Expr],Expr)] = Set.empty

    //  def rec(expr: Expr, path: List[Expr]) : Unit = {
    //    if(matcher(expr)) {
    //      collected = collected + ((path.reverse, expr))
    //    }

    //    expr match {
    //      case Let(i,e,b) => {
    //        rec(e, path)
    //        rec(b, Equals(Variable(i), e) :: path)
    //      }
    //      case IfExpr(cond, thenn, elze) => {
    //        rec(cond, path)
    //        rec(thenn, cond :: path)
    //        rec(elze, Not(cond) :: path)
    //      }
    //      case NAryOperator(args, _) => args.foreach(rec(_, path))
    //      case BinaryOperator(t1, t2, _) => rec(t1, path); rec(t2, path)
    //      case UnaryOperator(t, _) => rec(t, path)
    //      case t : Terminal => ;
    //      case _ => scala.sys.error("Unhandled tree in collectWithPathCondition : " + expr)
    //    }
    //  }

    //  rec(expression, Nil)
    //  collected
    //}
}
