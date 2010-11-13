import scala.collection.immutable.Set

object LambdaEval { 
  sealed abstract class Expr
  case class Const(value: Int) extends Expr
  case class Plus(lhs: Expr, rhs: Expr) extends Expr
  case class Lam(x: Int, body: Expr) extends Expr
  case class Pair(fst: Expr, snd: Expr) extends Expr
  case class Var(name: Int) extends Expr
  case class App(lhs: Expr, rhs: Expr) extends Expr
  case class Fst(e: Expr) extends Expr
  case class Snd(e: Expr) extends Expr

  // Checks whether the expression is a value
  def ok(expr: Expr): Boolean = expr match {
    case Const(_) => true
    case Lam(_,_) => true
    case Pair(e1, e2) => ok(e1) && ok(e2)
    case Var(_) => false
    case Plus(_,_) => false
    case App(_,_) => false
    case Fst(_) => false
    case Snd(_) => false
  }

  def okPair(p: StoreExprPair): Boolean = p match {
    case StoreExprPair(_, res) => ok(res)
  }

  sealed abstract class List
  case class Cons(head: BindingPair, tail: List) extends List
  case class Nil() extends List

  sealed abstract class AbstractPair
  case class BindingPair(key: Int, value: Expr) extends AbstractPair
  case class StoreExprPair(store: List, expr: Expr) extends AbstractPair


  // Find first element in list that has first component 'x' and return its
  // second component, analogous to List.assoc in OCaml
  def find(x: Int, l: List): Expr = l match {
    case Cons(i, is) => if (i.key == x) i.value else find(x, is)
  }

  // Evaluator
  def eval(store: List, expr: Expr): StoreExprPair = (expr match {
    case Const(i) => StoreExprPair(store, Const(i))
    case Var(x) => StoreExprPair(store, find(x, store))
    case Plus(e1, e2) =>
      val i1 = eval(store, e1) match {
        case StoreExprPair(_, Const(i)) => i
      }
      val i2 = eval(store, e2) match {
        case StoreExprPair(_, Const(i)) => i
      }
      StoreExprPair(store, Const(i1 + i2))
    case App(e1, e2) =>
      val store1 = eval(store, e1) match {
        case StoreExprPair(resS,_) => resS
      }
      val x = eval(store, e1) match {
        case StoreExprPair(_, Lam(resX, _)) => resX
      }
      val e = eval(store, e1) match {
        case StoreExprPair(_, Lam(_, resE)) => resE
      }
      /*
      val StoreExprPair(store1, Lam(x, e)) = eval(store, e1) match {
        case StoreExprPair(resS, Lam(resX, resE)) => StoreExprPair(resS, Lam(resX, resE))
      }
      */
      val v2 = eval(store, e2) match {
        case StoreExprPair(_, v) => v
      }
      eval(Cons(BindingPair(x, v2), store1), e)
    case Lam(x, e) => StoreExprPair(store, Lam(x, e))
    case Pair(e1, e2) =>
      val v1 = eval(store, e1) match {
        case StoreExprPair(_, v) => v
      }
      val v2 = eval(store, e2) match {
        case StoreExprPair(_, v) => v
      }
      StoreExprPair(store, Pair(v1, v2))
    case Fst(e) =>
      eval(store, e) match {
        case StoreExprPair(_, Pair(v1, _)) => StoreExprPair(store, v1)
      }
    case Snd(e) =>
      eval(store, e) match {
        case StoreExprPair(_, Pair(_, v2)) => StoreExprPair(store, v2)
      }
  }) ensuring(res => okPair(res))
  /*ensuring(res => res match {
    case StoreExprPair(_, resExpr) => ok(resExpr)
  }) */

  
}
