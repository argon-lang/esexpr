package esexpr

import cats.*
import cats.data.{NonEmptySeq, NonEmptyList, NonEmptyVector}
import cats.implicits.given
import esexpr.ESExprCodec.DecodeError
import esexpr.unsigned.*

import scala.deriving.Mirror
import scala.quoted.*
import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror.ProductOf

trait ESExprCodec[T] {
  lazy val tags: Set[ESExprTag]
  def encode(value: T): ESExpr
  def decode(expr: ESExpr): Either[DecodeError, T]
}

object ESExprCodec extends ESExprCodecDerivation[ESExprCodec] {

  enum ErrorPath {
    case Current
    case Constructor(constructor: String)
    case Positional(constructor: String, pos: Int, next: ErrorPath)
    case Keyword(constructor: String, name: String, next: ErrorPath)
  }

  final case class DecodeError(message: String, path: ErrorPath) extends ESExprDecodeException(message + " " + path.toString)


  given ESExprCodec[String] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Str)
    override def encode(value: String): ESExpr =
      ESExpr.Str(value)

    override def decode(expr: ESExpr): Either[DecodeError, String] =
      expr match {
        case ESExpr.Str(s) => Right(s)
        case _ => Left(DecodeError("Expected a string", ErrorPath.Current))
      }
  end given

  given ESExprCodec[IArray[Byte]] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Str)
    override def encode(value: IArray[Byte]): ESExpr =
      ESExpr.Binary(value)

    override def decode(expr: ESExpr): Either[DecodeError, IArray[Byte]] =
      expr match {
        case ESExpr.Binary(b) => Right(b)
        case _ => Left(DecodeError("Expected a binary value", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Boolean] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Bool)
    override def encode(value: Boolean): ESExpr =
      ESExpr.Bool(value)

    override def decode(expr: ESExpr): Either[DecodeError, Boolean] =
      expr match {
        case ESExpr.Bool(b) => Right(b)
        case _ => Left(DecodeError("Expected a bool", ErrorPath.Current))
      }
  end given

  given ESExprCodec[BigInt] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: BigInt): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, BigInt] =
      expr match {
        case ESExpr.Int(n) => Right(n)
        case _ => Left(DecodeError("Expected an int", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Byte] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: Byte): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, Byte] =
      expr match {
        case ESExpr.Int(n) if n >= Byte.MinValue && n <= Byte.MaxValue => Right(n.toByte)
        case _ => Left(DecodeError("Expected an int within the range of an 8-bit siged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UByte] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: UByte): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, UByte] =
      expr match {
        case ESExpr.Int(n) if n >= BigInt(UByte.MinValue) && n <= BigInt(UByte.MaxValue) => Right(n.toUByte)
        case _ => Left(DecodeError("Expected an int within the range of an 8-bit unsiged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Short] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: Short): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, Short] =
      expr match {
        case ESExpr.Int(n) if n >= Short.MinValue && n <= Short.MaxValue => Right(n.toShort)
        case _ => Left(DecodeError("Expected an int within the range of a 16-bit siged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UShort] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: UShort): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, UShort] =
      expr match {
        case ESExpr.Int(n) if n >= BigInt(UShort.MinValue) && n <= BigInt(UShort.MaxValue) => Right(n.toUShort)
        case _ => Left(DecodeError("Expected an int within the range of an 16-bit unsiged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Int] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: Int): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, Int] =
      expr match {
        case ESExpr.Int(n) if n >= Int.MinValue && n <= Int.MaxValue => Right(n.toInt)
        case _ => Left(DecodeError("Expected an int within the range of a 32-bit siged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UInt] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: UInt): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, UInt] =
      expr match {
        case ESExpr.Int(n) if n >= UInt.MinValue && n <= UInt.MaxValue => Right(n.toUInt)
        case _ => Left(DecodeError("Expected an int within the range of an 16-bit unsiged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Long] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Int)
    override def encode(value: Long): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, Long] =
      expr match {
        case ESExpr.Int(n) if n >= Long.MinValue && n <= Long.MaxValue => Right(n.toLong)
        case _ => Left(DecodeError("Expected an int within the range of a 64-bit siged integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Double] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Float64)
    override def encode(value: Double): ESExpr =
      ESExpr.Float64(value)

    override def decode(expr: ESExpr): Either[DecodeError, Double] =
      expr match {
        case ESExpr.Float64(f) => Right(f)
        case _ => Left(DecodeError("Expected a float64", ErrorPath.Current))
      }
  end given

  given ESExprCodec[ESExpr] with
    override lazy val tags: Set[ESExprTag] = Set.empty
    override def encode(value: ESExpr): ESExpr = value
    override def decode(expr: ESExpr): Either[DecodeError, ESExpr] = Right(expr)
  end given

  given [A: ESExprCodec]: ESExprCodec[Seq[A]] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor("list"))

    override def encode(value: Seq[A]): ESExpr =
      ESExpr.Constructor(
        "list",
        value.map(summon[ESExprCodec[A]].encode),
        Map(),
      )

    override def decode(expr: ESExpr): Either[DecodeError, Seq[A]] =
      expr match {
        case expr: ESExpr.Constructor =>
          for
            _ <- if expr.constructor == "list" then Right(()) else Left(DecodeError(s"Invalid constructor name for list: ${expr.constructor}", ErrorPath.Current))
            _ <- if expr.kwargs.isEmpty then Right(()) else Left(DecodeError(s"Unexpected keyword arguments for list: ${expr.constructor}", ErrorPath.Current))
            values <- expr.args.zipWithIndex
              .traverse((arg, i) => summon[ESExprCodec[A]].decode(arg).left.map(error => DecodeError(error.message, ErrorPath.Positional("list", i, error.path))))
          yield values

        case _ => Left(DecodeError("Expected constructor for list", ErrorPath.Current))
      }
  end given

  given[A: ESExprCodec]: ESExprCodec[NonEmptySeq[A]] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor("list"))

    override def encode(value: NonEmptySeq[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptySeq[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptySeq.fromSeq(values).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given[A: ESExprCodec]: ESExprCodec[NonEmptyList[A]] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor("list"))

    override def encode(value: NonEmptyList[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptyList[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptyList.fromList(values.toList).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given[A: ESExprCodec]: ESExprCodec[NonEmptyVector[A]] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor("list"))

    override def encode(value: NonEmptyVector[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptyVector[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptyVector.fromVector(values.toVector).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given[A: ESExprCodec]: ESExprCodec[Option[A]] with
    override lazy val tags: Set[ESExprTag] = summon[ESExprCodec[A]].tags + ESExprTag.Null

    override def encode(value: Option[A]): ESExpr =
      value.fold(ESExpr.Null)(summon[ESExprCodec[A]].encode)

    override def decode(expr: ESExpr): Either[DecodeError, Option[A]] =
      expr match {
        case ESExpr.Null => Right(None)
        case _ => summon[ESExprCodec[A]].decode(expr).map(Some.apply)
      }
  end given


  override def simpleEnumCodec[T](caseNames: Array[String], caseValues: Map[String, T])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
    new ESExprCodec[T] {
      override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Str)

      override def encode(value: T): ESExpr =
        ESExpr.Str(caseNames(m.ordinal(value)))

      override def decode(expr: ESExpr): Either[DecodeError, T] =
        expr match {
          case ESExpr.Str(s) =>
            caseValues.get(s).toRight { DecodeError(s"Invalid simple enum value: $s", ErrorPath.Current) }

          case _ =>
            Left(DecodeError("Expected a string for enum value", ErrorPath.Current))
        }
    }

  override def getCodecTags[T](codec: ESExprCodec[T]): Set[ESExprTag] =
    codec.tags

  override inline def derivedSumCreateCodec[T](codecMap: => Map[ESExprTag, WrappedCodec[ESExprCodec, ? <: T]])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
    ${ derivedSumMacro[T, m.MirroredElemTypes]('codecMap) }

  def derivedSumMacro[T: Type, SubTypes <: Tuple: Type](codecMap: Expr[Map[ESExprTag, WrappedCodec[ESExprCodec, ? <: T]]])(using q: Quotes): Expr[ESExprCodec[T]] =
    '{
      new ESExprCodec[T] {
        override lazy val tags: Set[ESExprTag] = ${codecMap}.keySet

        override def encode(value: T): ESExpr =
          ${
            MacroUtils.patternMatch[T, SubTypes, ESExpr]('value)([U] => (uValue: Expr[U], uType: Type[U]) => {
              given Type[U] = uType
              '{
                ESExprCodec.derived[U](using summonInline[Mirror.Of[U]]).encode($uValue)
              }
            })
          }

        override def decode(expr: ESExpr): Either[DecodeError, T] =
          val tag = ESExprTag.fromExpr(expr)

          ${codecMap}.get(tag).toRight(DecodeError(s"Unexpected tag: $tag (valid tags: ${tags})", ErrorPath.Current))
            .flatMap { codec => codec.codec.decode(expr) }
        end decode
      }
    }


  override type TCodecProduct[T] = ESExprCodecProduct[T]
  override type TCodecField[T] = ESExprCodecField[T]
  override type TCodecOptionalField[T] = ESExprOptionalFieldCodec[T]
  override type TCodecDict[T] = ESExprCodecDict[T]

  enum ProductErrorPath derives CanEqual {
    case Current
    case Positional(pos: Int, next: ErrorPath)
    case Keyword(name: String, next: ErrorPath)
  }

  final case class ProductDecodeError(message: String, path: ProductErrorPath) {
    def toDecodeError(constructor: String): DecodeError =
      DecodeError(message, path match {
        case ProductErrorPath.Current => ErrorPath.Constructor(constructor)
        case ProductErrorPath.Positional(pos, next) => ErrorPath.Positional(constructor, pos, next)
        case ProductErrorPath.Keyword(name, next) => ErrorPath.Keyword(constructor, name, next)
      })
  }

  trait ESExprCodecProduct[T] {
    def encode(value: T): (Map[String, ESExpr], List[ESExpr])
    def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, T]
  }

  trait ESExprCodecField[T] {
    def defaultValue: Option[T]
    def encode(value: T): Option[ESExpr]
    def decode(expr: ESExpr): Either[DecodeError, T]
  }

  trait ESExprOptionalFieldCodec[T] extends ESExprCodecField[T]

  trait ESExprCodecDict[T] {
    def encode(value: T): Map[String, ESExpr]
    def decode(kwargs: Map[String, ESExpr]): Either[ProductDecodeError, T]
  }

  given [T: ESExprCodec]: ESExprOptionalFieldCodec[Option[T]] with
    override def defaultValue: Option[Option[T]] = Some(None)

    override def encode(value: Option[T]): Option[ESExpr] =
      value.map(summon[ESExprCodec[T]].encode)

    override def decode(expr: ESExpr): Either[DecodeError, Option[T]] =
      summon[ESExprCodec[T]].decode(expr).map(Some.apply)
  end given

  given [T: ESExprCodec]: ESExprCodecDict[Map[String, T]] with
    override def encode(value: Map[String, T]): Map[String, ESExpr] =
        value.view.mapValues(summon[ESExprCodec[T]].encode).toMap

    override def decode(kwargs: Map[String, ESExpr]): Either[ProductDecodeError, Map[String, T]] =
      for
        decoded <- kwargs.toSeq.traverse((k, v) =>
          for
            v2 <- summon[ESExprCodec[T]].decode(v)
              .left.map { error => ProductDecodeError(error.message, ProductErrorPath.Keyword(k, error.path)) }
          yield k -> v2
        )
      yield decoded.toMap
  end given

  override inline def codecProductToCodec[T](using m: Mirror.ProductOf[T])(constructor: String, codecProduct: ESExprCodecProduct[m.MirroredElemTypes]): ESExprCodec[T] =
    new ESExprCodec[T] {
      override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor(constructor))

      override def encode(value: T): ESExpr =
        val (kwargs, args) = codecProduct.encode(
          Tuple.fromProductTyped[T & Product](
            summonInline[T =:= (T & Product)](value)
          )(using summonInline[Mirror.ProductOf[T] {type MirroredElemTypes = m.MirroredElemTypes} =:= Mirror.ProductOf[T & Product] {type MirroredElemTypes = m.MirroredElemTypes}](m))
        )

        ESExpr.Constructor(constructor, args, kwargs)
      end encode

      override def decode(expr: ESExpr): Either[DecodeError, T] =
        expr match {
          case expr: ESExpr.Constructor =>
            for
              _ <- if expr.constructor == constructor then Right(()) else Left(DecodeError(s"Unexpected constructor name: ${expr.constructor}", ErrorPath.Current))
              res <- codecProduct.decode(expr.kwargs, expr.args.toList)
                .left.map(_.toDecodeError(constructor))
            yield m.fromTuple(res)

          case _ =>
            Left(DecodeError("Expected a constructed value", ErrorPath.Current))
        }
    }

  override def optionalFieldCodec[Elem](elemCodec: ESExprCodec[Elem]): ESExprCodecField[Option[Elem]] =
    new ESExprCodecField[Option[Elem]] {
      override def defaultValue: Option[Option[Elem]] = Some(None)

      override def encode(value: Option[Elem]): Option[ESExpr] =
        value.map(elemCodec.encode)

      override def decode(expr: ESExpr): Either[DecodeError, Option[Elem]] =
        elemCodec.decode(expr).map(Some.apply)
    }


  override def fieldCodecWithDefault[Elem](elemCodec: ESExprCodec[Elem], defValue: Elem)(using CanEqual[Elem, Elem]): ESExprCodecField[Elem] =
    new ESExprCodecField[Elem] {
      override def defaultValue: Option[Elem] =
        Some(defValue)

      override def encode(value: Elem): Option[ESExpr] =
        if value == defValue then
          None
        else
          Some(elemCodec.encode(value))

      override def decode(expr: ESExpr): Either[DecodeError, Elem] = elemCodec.decode(expr)
    }

  override def codecToFieldCodec[Elem](elemCodec: ESExprCodec[Elem]): ESExprCodecField[Elem] =
    new ESExprCodecField[Elem] {
      override def defaultValue: Option[Elem] = None
      override def encode(value: Elem): Option[ESExpr] = Some(elemCodec.encode(value))
      override def decode(expr: ESExpr): Either[DecodeError, Elem] = elemCodec.decode(expr)
    }


  override def dictProductCodec[Dict, Tail <: Tuple](dictCodec: ESExprCodecDict[Dict], tailCodec: ESExprCodecProduct[Tail]): ESExprCodecProduct[Dict *: Tail] =
    new ESExprCodecProduct[Dict *: Tail] {
      override def encode(value: Dict *: Tail): (Map[String, ESExpr], List[ESExpr]) =
        val (h *: t) = value
        val (kwargs, args) = tailCodec.encode(t)
        (kwargs ++ dictCodec.encode(h), args)
      end encode

      override def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, Dict *: Tail] =
        for
          decoded <- dictCodec.decode(kwargs)
          tailDecoded <- tailCodec.decode(Map.empty, args)
        yield decoded *: tailDecoded
    }


  override def keywordProductCodec[A, Tail <: Tuple](keyName: String, fieldCodec: ESExprCodecField[A], tailCodec: ESExprCodecProduct[Tail]): ESExprCodecProduct[A *: Tail] =
    new ESExprCodecProduct[A *: Tail] {
      override def encode(value: A *: Tail): (Map[String, ESExpr], List[ESExpr]) =
        val (h *: t) = value
        val (kwargs, args) = tailCodec.encode(t)
        (fieldCodec.encode(h).fold(kwargs)(h => kwargs + (keyName -> h)), args)
      end encode

      override def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, A *: Tail] =
        for
          decoded <- kwargs.get(keyName) match {
            case Some(value) => fieldCodec.decode(value)
              .left.map { error => ProductDecodeError(error.message, ProductErrorPath.Keyword(keyName, error.path)) }
            case None => fieldCodec.defaultValue.toRight(ProductDecodeError(s"Required key $keyName was not provided", ProductErrorPath.Current))
          }
          tailDecoded <- tailCodec.decode(kwargs.removed(keyName), args)
        yield decoded *: tailDecoded

    }


  override def varargsProductCodec[Elem](typeName: String, elemCodec: ESExprCodec[Elem]): ESExprCodecProduct[Seq[Elem] *: EmptyTuple] =
    new ESExprCodecProduct[Seq[Elem] *: EmptyTuple] {
      override def encode(value: Seq[Elem] *: EmptyTuple): (Map[String, ESExpr], List[ESExpr]) =
        val (elems *: _) = value
        (Map.empty, elems.map(elemCodec.encode).toList)
      end encode

      override def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, Seq[Elem] *: EmptyTuple] =
        if kwargs.nonEmpty then
          Left(ProductDecodeError(s"Extra keyword arguments were provided for $typeName: ${kwargs.keySet}", ProductErrorPath.Current))
        else
          args.zipWithIndex
            .traverse((arg, i) =>
              elemCodec.decode(arg)
                .left.map(error => ProductDecodeError(error.message, ProductErrorPath.Positional(i, error.path)))
            )
            .map(_ *: EmptyTuple)
    }

  override def positionalProductCodec[Elem, Tail <: Tuple](elemCodec: ESExprCodec[Elem], tailCodec: ESExprCodecProduct[Tail]): ESExprCodecProduct[Elem *: Tail] =
    new ESExprCodecProduct[Elem *: Tail] {
      override def encode(value: Elem *: Tail): (Map[String, ESExpr], List[ESExpr]) =
        val (h *: t) = value
        val (kwargs, args) = tailCodec.encode(t)
        (kwargs, elemCodec.encode(h) :: args)
      end encode

      override def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, Elem *: Tail] =
        args match {
          case h :: t =>
            for
              decoded <- elemCodec.decode(h)
                .left.map(error => ProductDecodeError(error.message, ProductErrorPath.Positional(0, error.path)))
              tailDecoded <- tailCodec.decode(kwargs, t)
                .left.map(error => error.path match {
                  case ProductErrorPath.Positional(pos, path) =>
                    ProductDecodeError(error.message, ProductErrorPath.Positional(pos, path))

                  case _ => error
                })
            yield decoded *: tailDecoded

          case _ =>
            Left(ProductDecodeError("Not enough arguments were provided", ProductErrorPath.Current))
        }
    }


  override def emptyProductCodec: ESExprCodecProduct[EmptyTuple] =
    new ESExprCodecProduct[EmptyTuple] {
      override def encode(value: EmptyTuple): (Map[String, ESExpr], List[ESExpr]) = (Map.empty, List.empty)

      override def decode(kwargs: Map[String, ESExpr], args: List[ESExpr]): Either[ProductDecodeError, EmptyTuple] =
        if kwargs.nonEmpty || args.nonEmpty then
          Left(ProductDecodeError("Extra arguments were provided", ProductErrorPath.Current))
        else
          Right(EmptyTuple)
    }

  override def inlineCodec[T <: Product, Elem](elemCodec: ESExprCodec[Elem])(using m: ProductOf[T] {type MirroredElemTypes = Elem *: EmptyTuple}): ESExprCodec[T] =
    new ESExprCodec[T] {
      override lazy val tags: Set[ESExprTag] = elemCodec.tags

      override def encode(value: T): ESExpr = {
        val (elemValue *: EmptyTuple) = Tuple.fromProductTyped(value)
        elemCodec.encode(elemValue)
      }

      override def decode(expr: ESExpr): Either[DecodeError, T] =
        elemCodec.decode(expr).map(res => m.fromTuple(res *: EmptyTuple))
    }
}

