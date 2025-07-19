package esexpr

import cats.data.{NonEmptyList, NonEmptySeq, NonEmptyVector}
import esexpr.unsigned.UnsignedTypes.*
import zio.Chunk
import scala.quoted.{Expr, Type, Quotes}

trait ESExprTagSetProvider[A] {
  inline def tags: ESExprTagSet
}

object ESExprTagSetProvider {
  inline def tagsFor[A]: ESExprTagSet =
    ${ tagsForMacro[A] }

  private def tagsForMacro[A: Type](using q: Quotes): Expr[ESExprTagSet] =
    tagsForImpl(q.reflect.TypeRepr.of[A])

  private def tagsForImpl(using q: Quotes)(t: q.reflect.TypeRepr): Expr[ESExprTagSet] =
    import q.reflect.*

    val providerType = TypeRepr.of[ESExprTagSetProvider].appliedTo(t)
    Implicits.search(providerType) match {
      case success: ImplicitSearchSuccess =>
        def normalizeTree(tree: Term): Term =
          tree match {
            case Inlined(_, _, inner) => normalizeTree(inner)
            case Typed(inner, _) => normalizeTree(inner)
            case tree => tree
          }

        val normTerm = normalizeTree(success.tree)

        val tagsMethod = normTerm.tpe.typeSymbol
          .methodMember("tags")
          .filter { tagsMember => tagsMember.paramSymss.isEmpty }
          .headOption
          .getOrElse { report.errorAndAbort("Could not find tags method") }

        tagsMethod.tree match {
          case DefDef(_, _, _, Some(body)) => body.asExprOf[ESExprTagSet]
          case _ => report.errorAndAbort("Could not get tags method body")
        }

      case _ if t.typeSymbol.isTypeParam =>
        '{ ESExprTagSet.All }
        
      case _ =>
        getDerivedTags(
          t match {
            case tRef: TermRef => tRef.termSymbol
            case _ => t.typeSymbol
          }
        )
    }
  end tagsForImpl


  private def getDerivedTags(using q: Quotes)(t: q.reflect.Symbol): Expr[ESExprTagSet] =
    import q.reflect.*
    if t.flags.is(Flags.Case) then
      if t.hasAnnotation(TypeRepr.of[inlineValue].typeSymbol) then
        val inlineValueField = t.primaryConstructor.paramSymss.head.head
        val ivType = inlineValueField.tree match {
          case fieldTree: ValDef => fieldTree.tpt.tpe
          case _ => report.errorAndAbort("Could not get field type")
        }
        tagsForImpl(ivType)
      else
        val ctorName = ESExprCodec.CodecDerivation.getConstructor(t)
        Expr(ESExprTagSet.Empty.add(ESExprTag.Constructor(ctorName)))
      end if
    else if t.flags.is(Flags.Sealed) then
      if t.hasAnnotation(TypeRepr.of[simple].typeSymbol) then
        Expr[ESExprTagSet](ESExprTagSet.Cons(ESExprTag.Str, ESExprTagSet.Empty))
      else
        t.children.map(getDerivedTags).foldLeft[Expr[ESExprTagSet]]('{ ESExprTagSet.Empty }) {
          (a, b) =>
            '{ ESExprTagSet.union(${a}, ${b}) }
        }
    else
      report.errorAndAbort("Could not derive tags for type " + t)
  end getDerivedTags

  inline def tagsForVararg[T]: ESExprTagSet =
    ${ tagsForVarargMacro[T] }

  private def tagsForVarargMacro[T: Type](using q: Quotes): Expr[ESExprTagSet] =
    import q.reflect.*

    val varargCodecType = TypeRepr.of[VarargCodec[T]]
    val varargElementType = Implicits.search(varargCodecType) match {
      case success: ImplicitSearchSuccess =>
        TypeSelect(success.tree, "ElementType").tpe.dealiasKeepOpaques

      case _ =>
        report.errorAndAbort("Could not find vararg codec for vararg field")
    }

    tagsForImpl(varargElementType)
  end tagsForVarargMacro

  inline def tagsForOptional[T]: ESExprTagSet =
    ${ tagsForOptionalMacro[T] }

  private def tagsForOptionalMacro[T: Type](using q: Quotes): Expr[ESExprTagSet] =
    import q.reflect.*

    val optCodecType = TypeRepr.of[OptionalValueCodec[T]]
    val optElementType = Implicits.search(optCodecType) match {
      case success: ImplicitSearchSuccess =>
        TypeSelect(success.tree, "ElementType").tpe.dealiasKeepOpaques

      case _ =>
        report.errorAndAbort("Could not find optional value codec for optional field")
    }

    tagsForImpl(optElementType)
  end tagsForOptionalMacro



  inline given ESExprTagSetProvider[String]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Str, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Boolean]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Bool, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[BigInt]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Byte]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[UByte]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Short]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[UShort]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Int]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[UInt]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Long]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[ULong]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)
  end given

  inline given ESExprTagSetProvider[Float16]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float16, ESExprTagSet.Empty)
  end given

  inline given float16NaNTags: ESExprTagSetProvider[Either[ESExpr.Float16NaN, Float16]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float16, ESExprTagSet.Empty)
  end float16NaNTags

  inline given ESExprTagSetProvider[Float]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float32, ESExprTagSet.Empty)
  end given

  inline given float32NaNTags: ESExprTagSetProvider[Either[ESExpr.Float32NaN, Float]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float32, ESExprTagSet.Empty)
  end float32NaNTags

  inline given ESExprTagSetProvider[Double]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float64, ESExprTagSet.Empty)
  end given

  inline given float64NaNTags: ESExprTagSetProvider[Either[ESExpr.Float64NaN, Double]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float64, ESExprTagSet.Empty)
  end float64NaNTags

  inline given iarray8Tags: ESExprTagSetProvider[IArray[Byte]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)
  end iarray8Tags

  inline given chunk8Tags: ESExprTagSetProvider[Chunk[Byte]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)
  end chunk8Tags

  inline given iarray16Tags: ESExprTagSetProvider[IArray[UShort]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array16, ESExprTagSet.Empty)
  end iarray16Tags

  inline given chunk16Tags: ESExprTagSetProvider[Chunk[UShort]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array16, ESExprTagSet.Empty)
  end chunk16Tags

  inline given iarray32Tags: ESExprTagSetProvider[IArray[UInt]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array32, ESExprTagSet.Empty)
  end iarray32Tags

  inline given chunk32Tags: ESExprTagSetProvider[Chunk[UInt]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array32, ESExprTagSet.Empty)
  end chunk32Tags

  inline given iarray64Tags: ESExprTagSetProvider[IArray[ULong]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array64, ESExprTagSet.Empty)
  end iarray64Tags

  inline given chunk64Tags: ESExprTagSetProvider[Chunk[ULong]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array64, ESExprTagSet.Empty)
  end chunk64Tags

  inline given [A] => ESExprTagSetProvider[Seq[A]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)
  end given

  inline given [A] => ESExprTagSetProvider[NonEmptySeq[A]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)
  end given

  inline given [A] => ESExprTagSetProvider[NonEmptyList[A]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)
  end given

  inline given [A] => ESExprTagSetProvider[NonEmptyVector[A]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)
  end given

  inline given [A] => ESExprTagSetProvider[Option[A]]:
    override inline def tags: ESExprTagSet = ESExprTagSet.add(tagsFor[A], ESExprTag.Null)
  end given
}
