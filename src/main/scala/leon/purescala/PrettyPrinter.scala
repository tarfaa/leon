/* Copyright 2009-2013 EPFL, Lausanne */

package leon
package purescala

import Common._
import Trees._
import TypeTrees._
import Definitions._

import utils._

import java.lang.StringBuffer

/** This pretty-printer uses Unicode for some operators, to make sure we
 * distinguish PureScala from "real" Scala (and also because it's cute). */
class PrettyPrinter(opts: PrinterOptions, val sb: StringBuffer = new StringBuffer) {
  override def toString = sb.toString

  def append(str: String) {
    sb.append(str)
  }

  def ind(implicit lvl: Int) {
    sb.append("  " * lvl)
  }
  def nl(implicit lvl: Int) {
    sb.append("\n")
    ind(lvl)
  }

  // EXPRESSIONS
  // all expressions are printed in-line
  def ppUnary(expr: Tree, op1: String, op2: String)(implicit parent: Option[Tree], lvl: Int) {
    sb.append(op1)
    pp(expr, parent)
    sb.append(op2)
  }

  def ppBinary(left: Tree, right: Tree, op: String)(implicit  parent: Option[Tree], lvl: Int) {
    sb.append("(")
    pp(left, parent)
    sb.append(op)
    pp(right, parent)
    sb.append(")")
  }

  def ppNary(exprs: Seq[Tree], pre: String, op: String, post: String)(implicit  parent: Option[Tree], lvl: Int) {
    sb.append(pre)
    val sz = exprs.size
    var c = 0

    exprs.foreach(ex => {
      pp(ex, parent) ; c += 1 ; if(c < sz) sb.append(op)
    })

    sb.append(post)
  }

  def pp(tree: Tree, parent: Option[Tree])(implicit lvl: Int): Unit = {
    implicit val p = Some(tree)

    tree match {
      case id: Identifier =>
        if (opts.printUniqueIds) {
          sb.append(id.uniqueName)
        } else {
          sb.append(id.toString)
        }

      case Variable(id) =>
        if (opts.printTypes) {
          sb.append("(")
          pp(id, p)
          sb.append(": ")
          pp(id.getType, p)
          sb.append(")")
        } else {
          pp(id, p)
        }

      case LetTuple(bs,d,e) =>
        sb.append("(let ")
        ppNary(bs, "(", ",", " :=");
        pp(d, p)
        sb.append(") in")
        nl(lvl+1)
        pp(e, p)(lvl+1)
        sb.append(")")

      case Let(b,d,e) =>
        sb.append("(let (")
        pp(b, p)
        sb.append(" := ");
        pp(d, p)
        sb.append(") in")
        nl(lvl+1)
        pp(e, p)(lvl+1)
        sb.append(")")

      case LetDef(fd,body) =>
        sb.append("\n")
        pp(fd, p)(lvl+1)
        sb.append("\n")
        sb.append("\n")
        nl
        pp(body, p)

      case And(exprs) => ppNary(exprs, "(", " \u2227 ", ")")            // \land
      case Or(exprs) => ppNary(exprs, "(", " \u2228 ", ")")             // \lor
      case Not(Equals(l, r)) => ppBinary(l, r, " \u2260 ")    // \neq
      case Iff(l,r) => ppBinary(l, r, " <=> ")              
      case Implies(l,r) => ppBinary(l, r, " ==> ")              
      case UMinus(expr) => ppUnary(expr, "-(", ")")
      case Equals(l,r) => ppBinary(l, r, " == ")
      case IntLiteral(v) => sb.append(v)
      case BooleanLiteral(v) => sb.append(v)
      case StringLiteral(s) => sb.append("\"" + s + "\"")
      case UnitLiteral() => sb.append("()")
      case GenericValue(tp, id) =>
        pp(tp, p)
        sb.append("#"+id)

      case t@Tuple(exprs) => ppNary(exprs, "(", ", ", ")")
      case s@TupleSelect(t, i) =>
        pp(t, p)
        sb.append("._" + i)

      case c@Choose(vars, pred) =>
        sb.append("choose(")
        ppNary(vars, "", ", ", "")
        sb.append(" => ")
        pp(pred, p)
        sb.append(")")

      case CaseClass(cct, args) =>
        pp(cct, p)
        if (cct.classDef.isCaseObject) {
          ppNary(args, "", "", "")
        } else {
          ppNary(args, "(", ", ", ")")
        }

      case CaseClassInstanceOf(cct, e) =>
        pp(e, p)
        sb.append(".isInstanceOf[")
        pp(cct, p)
        sb.append("]")

      case CaseClassSelector(_, cc, id) =>
        pp(cc, p)
        sb.append(".")
        pp(id, p)

      case MethodInvocation(rec, _, tfd, args) =>
        pp(rec, p)
        sb.append(".")
        pp(tfd.id, p)

        if (tfd.tps.nonEmpty) {
          ppNary(tfd.tps, "[", ",", "]")
        }

        ppNary(args, "(", ", ", ")")

      case FunctionInvocation(tfd, args) =>
        pp(tfd.id, p)

        if (tfd.tps.nonEmpty) {
          ppNary(tfd.tps, "[", ",", "]")
        }

        ppNary(args, "(", ", ", ")")

      case Plus(l,r) => ppBinary(l, r, " + ")
      case Minus(l,r) => ppBinary(l, r, " - ")
      case Times(l,r) => ppBinary(l, r, " * ")
      case Division(l,r) => ppBinary(l, r, " / ")
      case Modulo(l,r) => ppBinary(l, r, " % ")
      case LessThan(l,r) => ppBinary(l, r, " < ")
      case GreaterThan(l,r) => ppBinary(l, r, " > ")
      case LessEquals(l,r) => ppBinary(l, r, " \u2264 ")      // \leq
      case GreaterEquals(l,r) => ppBinary(l, r, " \u2265 ")   // \geq
      case FiniteSet(rs) => if(rs.isEmpty) sb.append("\u2205") /* Ø */ else ppNary(rs, "{", ", ", "}")
      case FiniteMultiset(rs) => ppNary(rs, "{|", ", ", "|}")
      case EmptyMultiset(_) => sb.append("\u2205")                     // Ø
      case Not(ElementOfSet(s,e)) => ppBinary(s, e, " \u2209 ") // \notin
      case ElementOfSet(s,e) => ppBinary(s, e, " \u2208 ")    // \in
      case SubsetOf(l,r) => ppBinary(l, r, " \u2286 ")        // \subseteq
      case Not(SubsetOf(l,r)) => ppBinary(l, r, " \u2288 ")        // \notsubseteq
      case SetMin(s) => pp(s, p); sb.append(".min")
      case SetMax(s) => pp(s, p); sb.append(".max")
      case SetUnion(l,r) => ppBinary(l, r, " \u222A ")        // \cup
      case MultisetUnion(l,r) => ppBinary(l, r, " \u222A ")   // \cup
      case MapUnion(l,r) => ppBinary(l, r, " \u222A ")        // \cup
      case SetDifference(l,r) => ppBinary(l, r, " \\ ") 
      case MultisetDifference(l,r) => ppBinary(l, r, " \\ ")       
      case SetIntersection(l,r) => ppBinary(l, r, " \u2229 ") // \cap
      case MultisetIntersection(l,r) => ppBinary(l, r, " \u2229 ") // \cap
      case SetCardinality(t) => ppUnary(t, "|", "|")
      case MultisetCardinality(t) => ppUnary(t, "|", "|")
      case MultisetPlus(l,r) => ppBinary(l, r, " \u228E ")    // U+
      case MultisetToSet(e) => pp(e, p); sb.append(".toSet")
      case FiniteMap(rs) =>
        sb.append("{")
        val sz = rs.size
        var c = 0
        rs.foreach{case (k, v) => {
          pp(k, p); sb.append(" -> "); pp(v, p); c += 1 ; if(c < sz) sb.append(", ")
        }}
        sb.append("}")

      case MapGet(m,k) =>
        pp(m, p)
        ppNary(Seq(k), "(", ",", ")")

      case MapIsDefinedAt(m,k) =>
        pp(m, p)
        sb.append(".isDefinedAt")
        ppNary(Seq(k), "(", ",", ")")

      case ArrayLength(a) =>
        pp(a, p)
        sb.append(".length")

      case ArrayClone(a) => 
        pp(a, p)
        sb.append(".clone")

      case fill@ArrayFill(size, v) => 
        sb.append("Array.fill(")
        pp(size, p)
        sb.append(")(")
        pp(v, p)
        sb.append(")")

      case am@ArrayMake(v) =>
        sb.append("Array.make(")
        pp(v, p)
        sb.append(")")

      case sel@ArraySelect(ar, i) =>
        pp(ar, p)
        sb.append("(")
        pp(i, p)
        sb.append(")")

      case up@ArrayUpdated(ar, i, v) =>
        pp(ar, p)
        sb.append(".updated(")
        pp(i, p)
        sb.append(", ")
        pp(v, p)
        sb.append(")")
      
      case FiniteArray(exprs) =>
        ppNary(exprs, "Array(", ", ", ")")

      case Distinct(exprs) =>
        sb.append("distinct")
        ppNary(exprs, "(", ", ", ")")
      
      case IfExpr(c, t, e) =>
        sb.append("if (")
        pp(c, p)
        sb.append(")")
        nl(lvl+1)
        pp(t, p)(lvl+1)
        nl
        sb.append("else")
        nl(lvl+1)
        pp(e, p)(lvl+1)

      case mex @ MatchExpr(s, csc) =>
        pp(s, p)
        sb.append(" match {\n")

        csc.foreach(cs => {
          nl(lvl+1)
          pp(cs, p)
          sb.append("\n")
        })
        nl(lvl)
        sb.append("}")

      case Not(expr) => ppUnary(expr, "\u00AC(", ")")               // \neg

      case e @ Error(desc) =>
        sb.append("error(\"" + desc + "\")[")
        pp(e.getType, p)
        sb.append("]")

      case (tree: PrettyPrintable) => tree.printWith(this)

      // Cases
      case SimpleCase(pat, rhs) =>
          sb.append("case ")
          pp(pat, p)
          sb.append(" =>\n")
          ind(lvl+1)
          pp(rhs, p)(lvl+1)
      case GuardedCase(pat, guard, rhs) =>
          sb.append("case ")
          pp(pat, p)
          sb.append(" if ")
          pp(guard, p)
          sb.append(" =>\n")
          ind(lvl+1)
          pp(rhs, p)(lvl+1)

      // Patterns
      case CaseClassPattern(bndr, cct, subps) =>
        bndr.foreach(b => sb.append(b + " @ "))
        pp(cct, p)
        sb.append("(")
        var c = 0
        val sz = subps.size
        subps.foreach(sp => {
          pp(sp, p)
          if(c < sz - 1)
            sb.append(", ")
          c = c + 1
        })
        sb.append(")")

      case WildcardPattern(None)     => sb.append("_")
      case WildcardPattern(Some(id)) => pp(id, p)
      case InstanceOfPattern(bndr, cct) =>
        bndr.foreach(b => sb.append(b + " : "))
        pp(cct, p)

      case TuplePattern(bndr, subPatterns) =>
        bndr.foreach(b => sb.append(b + " @ "))
        sb.append("(")
        subPatterns.init.foreach(pat => {
          pp(pat, p)
          sb.append(", ")
        })
        pp(subPatterns.last, p)
        sb.append(")")


      // Types
      case Untyped => sb.append("???")
      case BottomType => sb.append("Nothing")
      case UnitType => sb.append("Unit")
      case Int32Type => sb.append("Int")
      case BooleanType => sb.append("Boolean")
      case ArrayType(bt) =>
        sb.append("Array[")
        pp(bt, p)
        sb.append("]")
      case SetType(bt) =>
        sb.append("Set[")
        pp(bt, p)
        sb.append("]")
      case MapType(ft,tt) =>
        sb.append("Map[")
        pp(ft, p)
        sb.append(",")
        pp(tt, p)
        sb.append("]")
      case MultisetType(bt) =>
        sb.append("Multiset[")
        pp(bt, p)
        sb.append("]")
      case TupleType(tpes) => ppNary(tpes, "(", ", ", ")")
      case FunctionType(fts, tt) =>
        if (fts.size > 1) {
          ppNary(fts, "(", ", ", ")")
        } else if (fts.size == 1) {
          pp(fts.head, p)
        }
        sb.append(" => ")
        pp(tt, p)

      case c: ClassType =>
        pp(c.classDef.id, p)
        if (c.tps.nonEmpty) {
          ppNary(c.tps, "[", ",", "]")
        }


      // Definitions
      case Program(id, modules) =>
        assert(lvl == 0)
        sb.append("package ")
        pp(id, p)
        sb.append(" {\n")
        modules.foreach {
          m => pp(m, p)(lvl+1)
        }
        sb.append("}\n")

      case ModuleDef(id, defs) =>
        nl
        sb.append("object ")
        pp(id, p)
        sb.append(" {")

        var c = 0
        val sz = defs.size

        defs.foreach(df => {
          pp(df, p)(lvl+1)
          if(c < sz - 1) {
            sb.append("\n\n")
          }
          c = c + 1
        })

        nl
        sb.append("}\n")

      case acd @ AbstractClassDef(id, tparams, parent) =>
        sb.append("sealed abstract class ")
        pp(id, p)

        if (tparams.nonEmpty) {
          ppNary(tparams, "[", ",", "]")
        }

        parent.foreach{ par =>
          sb.append(" extends ")
          pp(par.id, p)
        }

        if (acd.methods.nonEmpty) {
          sb.append(" {\n")
          for (md <- acd.methods) {
            ind(lvl+1)
            pp(md, p)(lvl+1)
            sb.append("\n\n")
          }
          ind
          sb.append("}")
        }

      case ccd @ CaseClassDef(id, tparams, parent, isObj) =>
        if (isObj) {
          sb.append("case object ")
        } else {
          sb.append("case class ")
        }

        pp(id, p)

        if (tparams.nonEmpty) {
          ppNary(tparams, "[", ", ", "]")
        }

        if (!isObj) {
          ppNary(ccd.fields, "(", ", ", ")")
        }

        parent.foreach{ par =>
          sb.append(" extends ")
          pp(par.id, p)
        }

        if (ccd.methods.nonEmpty) {
          sb.append(" {\n")
          for (md <- ccd.methods) {
            ind(lvl+1)
            pp(md, p)(lvl+1)
            sb.append("\n\n")
          }
          ind
          sb.append("}")
        }

      case th: This =>
        sb.append("this")

      case fd: FunDef =>
        for(a <- fd.annotations) {
          ind
          sb.append("@" + a + "\n")
        }

        fd.precondition.foreach(prec => {
          ind
          sb.append("@pre : ")
          pp(prec, p)(lvl)
          sb.append("\n")
        })

        fd.postcondition.foreach{ case (id, postc) => {
          ind
          sb.append("@post: ")
          pp(id, p)
          sb.append(" => ")
          pp(postc, p)(lvl)
          sb.append("\n")
        }}

        ind
        sb.append("def ")
        pp(fd.id, p)
        sb.append("(")

        val sz = fd.params.size
        var c = 0
        
        fd.params.foreach(arg => {
          sb.append(arg.id)
          sb.append(" : ")
          pp(arg.tpe, p)

          if(c < sz - 1) {
            sb.append(", ")
          }
          c = c + 1
        })

        sb.append(") : ")
        pp(fd.returnType, p)
        sb.append(" = ")
        fd.body match {
          case Some(body) =>
            pp(body, p)(lvl)

          case None =>
            sb.append("[unknown function implementation]")
        }

      case TypeParameterDef(tp) =>
        pp(tp, p)

      case TypeParameter(id) =>
        pp(id, p)

      case _ => sb.append("Tree? (" + tree.getClass + ")")
    }
    if (opts.printPositions) {
      ppos(tree.getPos)
    }
  }

  def ppos(p: Position) = p match {
    case op: OffsetPosition =>
      sb.append("@"+op.toString)
    case rp: RangePosition =>
      sb.append("@"+rp.focusBegin.toString+"--"+rp.focusEnd.toString)
    case _ =>
  }
}

trait PrettyPrintable {
  self: Tree =>

  def printWith(printer: PrettyPrinter)(implicit lvl: Int): Unit
}

class EquivalencePrettyPrinter(opts: PrinterOptions) extends PrettyPrinter(opts) {
  override def pp(tree: Tree, parent: Option[Tree])(implicit lvl: Int): Unit = {
    tree match {
      case id: Identifier =>
        sb.append(id.name)

      case _ =>
        super.pp(tree, parent)
    }
  }
}

abstract class PrettyPrinterFactory {
  def create(opts: PrinterOptions): PrettyPrinter

  def apply(tree: Tree, opts: PrinterOptions = PrinterOptions()): String = {
    val printer = create(opts)
    printer.pp(tree, None)(opts.baseIndent)
    printer.toString
  }

  def apply(tree: Tree, ctx: LeonContext): String = {
    val opts = PrinterOptions.fromContext(ctx)
    apply(tree, opts)
  }
}

object PrettyPrinter extends PrettyPrinterFactory {
  def create(opts: PrinterOptions) = new PrettyPrinter(opts)
}

object EquivalencePrettyPrinter extends PrettyPrinterFactory {
  def create(opts: PrinterOptions) = new EquivalencePrettyPrinter(opts)
}
