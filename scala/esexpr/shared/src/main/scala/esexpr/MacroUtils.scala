package esexpr

import scala.deriving.Mirror
import scala.quoted.*

private[esexpr] object MacroUtils {

  def patternMatch[T: Type, SubTypes <: Tuple : Type, A: Type](expr: Expr[T])(f: [U] => (Expr[U], Type[U]) => Expr[A])(using q: Quotes): Expr[A] =
    import q.reflect.{*, given}

    def createPatternBranch[U: Type]: CaseDef =
      val sym = Symbol.newBind(Symbol.spliceOwner, "v", Flags.EmptyFlags, TypeRepr.of[U])
      val varExpr = Ref(sym).asExprOf[U]
      CaseDef(
        Bind(sym, Typed(Wildcard(), TypeTree.of[U])),
        None,
        Block(Nil, f[U](varExpr, Type.of[U]).asTerm)
      )
    end createPatternBranch

    val cases = tupleForeach[SubTypes, CaseDef]([U] => (uType: Type[U]) => createPatternBranch[U](using uType))

    Match(expr.asTerm, cases).asExprOf[A]
  end patternMatch

  def tupleForeach[T <: Tuple : Type, A](f: [U] => Type[U] => A)(using q: Quotes): List[A] =
    Type.of[T] match {
      case '[h *: t] => f(Type.of[h]) :: tupleForeach[t, A](f)
      case '[EmptyTuple] => Nil
      case _ => throw new Exception(s"Unexpected type for tuple: ${Type.show[T]}")
    }
  end tupleForeach

  def tupleForeachPair[T1 <: Tuple : Type, T2 <: Tuple : Type, A](f: [U1, U2] => (Type[U1], Type[U2]) => A)(using q: Quotes): List[A] =
    (Type.of[T1], Type.of[T2]) match {
      case ('[h1 *: t1], '[h2 *: t2]) => f(Type.of[h1], Type.of[h2]) :: tupleForeachPair[t1, t2, A](f)
      case ('[EmptyTuple], '[EmptyTuple]) => Nil
      case _ => throw new Exception(s"Unexpected type for tuple pairs: ${Type.show[T1]} and ${Type.show[T2]}")
    }
  end tupleForeachPair

  inline def typeHasAnn[T, Ann]: Boolean =
    ${ typeHasAnnMacro[T, Ann] }

  private def typeHasAnnMacro[T: Type, Ann: Type](using q: Quotes): Expr[Boolean] =
    import q.reflect.{*, given}

    val tRep = TypeRepr.of[T]
    val t = tRep match {
      case tRep: TermRef => tRep.termSymbol
      case _ => tRep.typeSymbol
    }

    val res = t.hasAnnotation(TypeRepr.of[Ann].typeSymbol)

    Expr(res)
  end typeHasAnnMacro

  inline def typeGetAnn[T, Ann]: Ann =
    ${ typeGetAnnMacro[T, Ann] }

  private def typeGetAnnMacro[T: Type, Ann: Type](using q: Quotes): Expr[Ann] =
    import q.reflect.{*, given}

    val tRep = TypeRepr.of[T]
    val t = tRep match {
      case tRep: TermRef => tRep.termSymbol
      case _ => tRep.typeSymbol
    }

    val res = t.getAnnotation(TypeRepr.of[Ann].typeSymbol).getOrElse {
      throw new Exception(s"Could not find annotation of type $tRep")
    }

    res.asExprOf[Ann]
  end typeGetAnnMacro


  inline def typeHasVarArgs[T]: Boolean =
    ${ typeHasVarArgsMacro[T] }

  private def asMatchable[A](a: A): A & Matchable =
    a.asInstanceOf[A & Matchable]

  private def typeHasVarArgsMacro[T: Type](using q: Quotes): Expr[Boolean] =
    import q.reflect.{*, given}

    val tRep = TypeRepr.of[T]
    val t = tRep.typeSymbol

    val res = t
      .caseFields
      .lastOption
      .map(tRep.memberType)
      .map[TypeRepr & Matchable](asMatchable)
      .collect {
        case AnnotatedType(_, Apply(Select(New(t), "<init>"), List())) => t
      }
      .map[TypeRepr & Matchable] { t => asMatchable(t.tpe) }
      .collect {
        case TypeRef(t, "Repeated") => t
      }
      .map[TypeRepr & Matchable](asMatchable)
      .collect {
        case ThisType(t) => t
      }
      .map[TypeRepr & Matchable](asMatchable)
      .collect {
        case TypeRef(t, "internal") => t
      }
      .map[TypeRepr & Matchable](asMatchable)
      .collect {
        case NoPrefix() =>
      }
      .isDefined

    Expr(res)
  end typeHasVarArgsMacro


  inline def caseFieldHasAnn[T, Ann](fieldName: String): Boolean =
    ${ caseFieldHasAnnMacro[T, Ann]('fieldName) }

  private def caseFieldHasAnnMacro[T: Type, Ann: Type](fieldName: Expr[String])(using q: Quotes): Expr[Boolean] =
    import q.reflect.{*, given}

    val fieldNameStr = fieldName.valueOrAbort

    val tRep = TypeRepr.of[T]
    val t = tRep.typeSymbol

    val res = t.primaryConstructor
      .paramSymss
      .flatten
      .exists { param =>
        param.name == fieldNameStr &&
          param.hasAnnotation(TypeRepr.of[Ann].typeSymbol)
      }

    Expr(res)
  end caseFieldHasAnnMacro


  inline def caseFieldGetAnn[T, Ann](fieldName: String): Ann =
    ${ caseFieldGetAnnMacro[T, Ann]('fieldName) }

  private def caseFieldGetAnnMacro[T: Type, Ann: Type](fieldName: Expr[String])(using q: Quotes): Expr[Ann] =
    import q.reflect.{*, given}

    val fieldNameStr = fieldName.valueOrAbort

    val tRep = TypeRepr.of[T]
    val t = tRep.typeSymbol

    val res = t.primaryConstructor
      .paramSymss
      .flatten
      .filter(param => param.name == fieldNameStr)
      .flatMap(param => param.getAnnotation(TypeRepr.of[Ann].typeSymbol))
      .headOption
      .getOrElse(throw new Exception("Could not find annotation"))

    res.asExprOf[Ann]
  end caseFieldGetAnnMacro


  inline def caseFieldHasDefaultValue[T, A](fieldName: String): Boolean =
    ${ caseFieldHasDefaultValueMacro[T, A]('fieldName) }

  private def caseFieldHasDefaultValueMacro[T: Type, A: Type](fieldName: Expr[String])(using q: Quotes): Expr[Boolean] =
    import q.reflect.{*, given}

    val fieldNameStr = fieldName.valueOrAbort


    val tRep = TypeRepr.of[T]
    val t = tRep.typeSymbol
    val compClass = t.companionClass

    val index = t.caseFields.indexWhere(_.name == fieldNameStr)

    val res = compClass.tree match {
      case ClassDef(_, _, _, _, body) =>
        body
          .exists {
            case dd: DefDef =>
              dd.name == "$lessinit$greater$default$" + (index + 1)
            case _ => false
          }

      case _ => false
    }

    Expr(res)
  end caseFieldHasDefaultValueMacro

  inline def caseFieldDefaultValue[T, A](fieldName: String): A =
    ${ caseFieldDefaultValueMacro[T, A]('fieldName) }

  def caseFieldDefaultValueMacro[T: Type, A: Type](fieldName: Expr[String])(using q: Quotes): Expr[A] =
    import q.reflect.{*, given}

    val fieldNameStr = fieldName.valueOrAbort

    val tRep = TypeRepr.of[T]
    val t = tRep.typeSymbol
    val compClass = t.companionClass
    val compMod = Ref(t.companionModule)

    val index = t.caseFields.indexWhere(_.name == fieldNameStr)

    val defaultValue = compClass.tree match {
      case ClassDef(_, _, _, _, body) =>
        body
          .collectFirst {
            case dd: DefDef if dd.name == "$lessinit$greater$default$" + (index + 1) =>
              compMod.select(dd.symbol)
          }
          .getOrElse {
            throw new Exception("Could not find default case field value")
          }

      case _ => throw new Exception("Could not find default case field value")
    }

    defaultValue.asExprOf[A]
  end caseFieldDefaultValueMacro


}
