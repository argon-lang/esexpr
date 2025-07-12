package esexpr

import scala.quoted.{Expr, FromExpr, Quotes, ToExpr}
import scala.compiletime.{asMatchable, error}

sealed trait ESExprTagSet derives CanEqual {
  def add(x: ESExprTag): ESExprTagSet
  def contains(tag: ESExprTag): Boolean
  def isAll: Boolean
  def isEmpty: Boolean
  def | (other: ESExprTagSet): ESExprTagSet
  def isDisjoint(other: ESExprTagSet): Boolean

  override def equals(obj: Any): Boolean =
    obj.asMatchable match {
      case other: ESExprTagSet =>
        (this, other) match {
          case (_: ESExprTagSet.All.type, _: ESExprTagSet.All.type) => true
          case (_: ESExprTagSet.All.type, _) | (_, _: ESExprTagSet.All.type) => false
          case (a: ESExprTagSet.Finite, b: ESExprTagSet.Finite) =>
            a.toSet == b.toSet
        }

      case _ => false
    }

  override def hashCode(): Int =
    this match {
      case ESExprTagSet.All => 7
      case finite: ESExprTagSet.Finite => finite.toSet.hashCode()
    }
}

object ESExprTagSet {
  def apply(tags: ESExprTag*): ESExprTagSet =
    tags.foldLeft[ESExprTagSet](ESExprTagSet.Empty)(_.add(_))

  sealed trait Finite extends ESExprTagSet {
    override def add(x: ESExprTag): ESExprTagSet = Cons(x, this)

    override def isAll: Boolean = false

    override def | (other: ESExprTagSet): ESExprTagSet =
      other match {
        case Cons(head, tail) => add(head) | tail
        case _: Empty.type => this
        case _: All.type => All
      }

    def toSet: Set[ESExprTag]
  }

  final case class Cons(head: ESExprTag, tail: Finite) extends Finite {
    override def contains(tag: ESExprTag): Boolean =
      head == tag || tail.contains(tag)

    override def isEmpty: Boolean = false

    override def toSet: Set[ESExprTag] = tail.toSet ++ Set(head)

    override def isDisjoint(other: ESExprTagSet): Boolean =
      !other.contains(head) && tail.isDisjoint(other)
  }
  case object Empty extends Finite {
    override def contains(tag: ESExprTag): Boolean =
      false

    override def isEmpty: Boolean = true

    override def toSet: Set[ESExprTag] = Set.empty

    override def isDisjoint(other: ESExprTagSet): Boolean = true
  }
  case object All extends ESExprTagSet {
    override def | (other: ESExprTagSet): ESExprTagSet = this
    override def contains(tag: ESExprTag): Boolean = true
    override def isEmpty: Boolean = false
    override def isAll: Boolean = true
    override def add(x: ESExprTag): ESExprTagSet = this
    override def isDisjoint(other: ESExprTagSet): Boolean =
      other.isEmpty
  }

  inline def isEmpty(inline a: ESExprTagSet): Boolean =
    inline a match
      case _: ESExprTagSet.Empty.type => true
      case _ => false
    end match

  inline def isAll(inline a: ESExprTagSet): Boolean =
    inline a match
      case _: ESExprTagSet.All.type => true
      case _ => false
    end match

  inline def finiteToSet(inline a: ESExprTagSet): Set[ESExprTag] =
    inline a match
      case a: ESExprTagSet.Finite => a.toSet
    end match

  inline def isDisjoint(inline a: ESExprTagSet, inline b: ESExprTagSet): Boolean =
    inline a match
      case _: All.type => isEmpty(b)
      case _: Empty.type => true
      case _: Cons if contains(b, head(a)) => false
      case _: Cons => isDisjoint(tail(a), b)
    end match

  inline def head(inline a: ESExprTagSet): ESExprTag =
    ${ headMacro('a) }

  private def headMacro(a: Expr[ESExprTagSet])(using Quotes): Expr[ESExprTag] =
    a match {
      case '{ Cons($headValue, $_) } => headValue
      case _ =>
        val aStr = Expr(a.show)
        '{ error("Cannot get tag set head from " + $aStr) }
    }

  inline def tail(inline a: ESExprTagSet): ESExprTagSet =
    ${ tailMacro('a) }

  private def tailMacro(a: Expr[ESExprTagSet])(using Quotes): Expr[ESExprTagSet] =
    a match {
      case '{ Cons($_, $tailValue) } => tailValue
      case _ =>
        val aStr = Expr(a.show)
        '{ error("Cannot get tag set tail from " + $aStr) }
    }

  inline def contains(inline a: ESExprTagSet, inline x: ESExprTag): Boolean =
    inline a match
      case _: ESExprTagSet.All.type => true
      case _: ESExprTagSet.Empty.type => false
      case _: ESExprTagSet.Cons =>
        inline if ESExprTag.tagEqualsInline(head(a), x) then
          true
        else
          contains(tail(a), x)
    end match


  inline def add(inline a: ESExprTagSet, inline b: ESExprTag): ESExprTagSet =
    ${ addMacro('a, 'b) }

  private def addMacro(a: Expr[ESExprTagSet], b: Expr[ESExprTag])(using q: Quotes): Expr[ESExprTagSet] =
    import q.reflect.*

    Expr(a.valueOrAbort.add(b.valueOrAbort))
  end addMacro

  inline def show(inline a: ESExprTagSet): String =
    ${ showMacro('a) }

  private def showMacro(a: Expr[ESExprTagSet])(using q: Quotes): Expr[String] =
    Expr(a.valueOrAbort.toString)
  end showMacro


  inline def union(inline a: ESExprTagSet, inline b: ESExprTagSet): ESExprTagSet =
    inline a match
      case _: Cons => union(tail(a), add(b, head(a)))
      case _: Empty.type => b
      case _: All.type => All
    end match

  given FromExpr[ESExprTagSet]:
    override def unapply(x: Expr[ESExprTagSet])(using Quotes): Option[ESExprTagSet] =
      x match {
        case '{ Cons($head, $tail) } =>
          for
            head <- head.value
            tail <- tail.value
          yield Cons(head, tail)

        case '{ Empty } => Some(Empty)
        case '{ All } => Some(All)
        case _ => None
      }
  end given

  given FromExpr[Finite]:
    override def unapply(x: Expr[Finite])(using Quotes): Option[Finite] =
      x match {
        case '{ Cons($head, $tail) } =>
          for
            head <- head.value
            tail <- tail.value
          yield Cons(head, tail)

        case '{ Empty } => Some(Empty)
        case _ => None
      }
  end given

  given ToExpr[ESExprTagSet]:
    override def apply(x: ESExprTagSet)(using Quotes): Expr[ESExprTagSet] =
      x match {
        case All => '{ All }
        case Empty => '{ Empty }
        case Cons(head, tail) =>
          val head2 = Expr(head)
          val tail2 = Expr(tail)
          '{ Cons($head2, $tail2) }
      }
  end given

  given ToExpr[Finite]:
    override def apply(x: Finite)(using Quotes): Expr[Finite] =
      x match {
        case Empty => '{ Empty }
        case Cons(head, tail) =>
          val head2 = Expr(head)
          val tail2 = Expr(tail)
          '{ Cons($head2, $tail2) }
      }
  end given

}
