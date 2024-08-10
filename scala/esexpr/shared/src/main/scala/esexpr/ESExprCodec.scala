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

object ESExprCodec {

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

  given ESExprCodec[Float] with
    override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Float64)
    override def encode(value: Float): ESExpr =
      ESExpr.Float64(value)

    override def decode(expr: ESExpr): Either[DecodeError, Float] =
      expr match {
        case ESExpr.Float32(f) => Right(f)
        case _ => Left(DecodeError("Expected a float32", ErrorPath.Current))
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

  export CodecDerivation.derived

  object CodecDerivation:
    import MacroUtils.*
    inline def derived[T](using m: Mirror.Of[T]): ESExprCodec[T] =
      inline m match {
        case m: Mirror.SumOf[T] => derivedSum[T](using m)
        case m: Mirror.ProductOf[T] => derivedProduct[T](using m)
      }


    inline def derivedSum[T](using m: Mirror.SumOf[T]): ESExprCodec[T] =
      inline if typeHasAnn[T, simple] then
        val caseNames = simpleEnumCaseNames[m.MirroredElemLabels, m.MirroredElemTypes]
        val caseValues = simpleEnumCaseValues[T, m.MirroredElemTypes]
        simpleEnumCodec(caseNames.toArray, caseNames.zip(caseValues).toMap)
      else
        lazy val codecMap = buildSumCodecs[T, m.MirroredElemTypes]
        derivedSumCreateCodec(codecMap)
      end if

    inline def simpleEnumCaseNames[Labels <: Tuple, Cases <: Tuple]: List[String] =
      inline (erasedValue[Cases], erasedValue[Labels]) match
        case _: (head *: tail, headLabel *: tailLabels) =>
          val name =
            inline if typeHasAnn[head, constructor] then
              typeGetAnn[head, constructor].name
            else
              toSExprName(constValue[headLabel & String])

          name :: simpleEnumCaseNames[tailLabels, tail]

        case _: (EmptyTuple, EmptyTuple) =>
          Nil
      end match

    inline def simpleEnumCaseValues[T, Cases <: Tuple](using m: Mirror.SumOf[T]): List[T] =
      inline erasedValue[Cases] match
        case _: (head *: tail) =>
          val value = summonInline[Mirror.ProductOf[head] { type MirroredElemTypes = EmptyTuple }].fromProductTyped(EmptyTuple)
          summonInline[head <:< T](value) :: simpleEnumCaseValues[T, tail]

        case _: EmptyTuple =>
          Nil
      end match


    inline def buildSumCodecs[T, SubTypes <: Tuple]: Map[ESExprTag, ESExprCodec[? <: T]] =
      inline erasedValue[SubTypes] match
        case _: (htype *: ttypes) =>
          val hcodec =  derived[htype](using summonInline[Mirror.Of[htype]])
          val tailMap = buildSumCodecs[T, ttypes]
          val hcodec2 = summonInline[ESExprCodec[htype] <:< ESExprCodec[? <: T]](hcodec)

          tailMap ++ hcodec.tags.toSeq.map(_ -> hcodec2)

        case _: EmptyTuple => Map.empty
      end match


    inline def derivedProduct[T](using m: Mirror.ProductOf[T]): ESExprCodec[T] =
      inline if typeHasAnn[T, inlineValue] then

        inline erasedValue[m.MirroredElemTypes] match {
          case _: (elem *: EmptyTuple) =>
            lazy val elemCodec = summonInline[ESExprCodec[elem]]

            type InnerMirror = Mirror.ProductOf[T & Product] { type MirroredElemTypes = elem *: EmptyTuple }
            val codec = inlineValueCodec[T & Product, elem](elemCodec)(using summonInline[m.type <:< InnerMirror](m))
            summonInline[ESExprCodec[T & Product] =:= ESExprCodec[T]](codec)
        }

      else
        val derivedTuple = derivedProductTuple[T, m.MirroredLabel, m.MirroredElemLabels, m.MirroredElemTypes]
        val constructor =
          inline if typeHasAnn[T, constructor] then
            typeGetAnn[T, constructor].name
          else
            toSExprName(constValue[m.MirroredLabel])

        new ESExprCodec[T] {
          override lazy val tags: Set[ESExprTag] = Set(ESExprTag.Constructor(constructor))

          override def encode(value: T): ESExpr =
            val (args, kwargs) = derivedTuple.encode(
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
                  (res, state) <- derivedTuple.decode(ProductDecodeState(0, expr.args, expr.kwargs))
                    .left.map(_.toDecodeError(constructor))
                  
                  _ <-
                    if state.args.nonEmpty || state.kwargs.nonEmpty then
                      Left(DecodeError("Extra arguments were provided", ErrorPath.Current))
                    else
                      Right(())

                yield m.fromTuple(res)

              case _ =>
                Left(DecodeError("Expected a constructed value", ErrorPath.Current))
            }
        }
      end if

    trait ESExprCodecProduct[T] {
      def encode(value: T): (Seq[ESExpr], Map[String, ESExpr])
      def decode(state: ProductDecodeState): Either[ProductDecodeError, (T, ProductDecodeState)]
    }

    final case class ProductDecodeState(positionalIndex: Int, args: Seq[ESExpr], kwargs: Map[String, ESExpr])

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

    inline def derivedProductTuple[T, TypeLabel <: String, Labels <: Tuple, Types <: Tuple]: ESExprCodecProduct[Types] =
      inline (erasedValue[Labels], erasedValue[Types]) match
        case _: ((hlabel *: tlabels), (htype *: ttype)) =>
          val fieldCodec =
            inline if caseFieldHasAnn[T, keyword](constValue[hlabel & String]) then
              val keyName =
                val name = caseFieldGetAnn[T, keyword](constValue[hlabel & String]).name
                if name.isEmpty then
                  toSExprName(constValue[hlabel & String])
                else
                  name
              end keyName
              

              inline if caseFieldHasAnn[T, optional](constValue[hlabel & String]) then
                lazy val optionalValueCodec = summonInline[OptionalValueCodec[htype]]
                optionalKeywordProductCodec(keyName, optionalValueCodec)
              else
                lazy val valueCodec = summonInline[ESExprCodec[htype]]

                if caseFieldHasDefaultValue[T, htype](constValue[hlabel & String]) then
                  val canEqual_HType = summonInline[CanEqual[htype, htype]]
                  defaultKeywordProductCodec(keyName, valueCodec, caseFieldDefaultValue[T, htype](constValue[hlabel & String]))(using canEqual_HType)
                else if caseFieldHasAnn[T, defaultValue[?]](constValue[hlabel & String]) then
                  val canEqual_HType = summonInline[CanEqual[htype, htype]]
                  defaultKeywordProductCodec(keyName, valueCodec, caseFieldGetAnn[T, defaultValue[htype]](constValue[hlabel & String]).value)(using canEqual_HType)
                else
                  requiredKeywordProductCodec(keyName, valueCodec)
                end if
              end if

            else if caseFieldHasAnn[T, vararg](constValue[hlabel & String]) then
              lazy val varargCodec = summonInline[VarargCodec[htype]]
              varargProductCodec(varargCodec)

            else if caseFieldHasAnn[T, dict](constValue[hlabel & String]) then
              lazy val dictCodec = summonInline[DictCodec[htype]]
              dictProductCodec(dictCodec)

            else
              inline if caseFieldHasAnn[T, optional](constValue[hlabel & String]) then
                lazy val optionalValueCodec = summonInline[OptionalValueCodec[htype]]
                optionalPositionalProductCodec(optionalValueCodec)
              else
                lazy val valueCodec = summonInline[ESExprCodec[htype]]
                requiredPositionalProductCodec(valueCodec)
              end if
            end if

          val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype]

          val codec = new ESExprCodecProduct[htype *: ttype] {
            override def encode(value: htype *: ttype): (Seq[ESExpr], Map[String, ESExpr]) =
              val (head *: tail) = value

              val (args1, kwargs1) = fieldCodec.encode(head)
              val (args2, kwargs2) = tailCodec.encode(tail)
              (args1 ++ args2, kwargs1 ++ kwargs2)
            end encode

            override def decode(state: ProductDecodeState): Either[ProductDecodeError, (htype *: ttype, ProductDecodeState)] =
              for
                (h, state) <- fieldCodec.decode(state)
                (t, state) <- tailCodec.decode(state)
              yield (h *: t, state)
          }

          summonInline[ESExprCodecProduct[htype *: ttype] <:< ESExprCodecProduct[Types]](codec)

        case _: (EmptyTuple, EmptyTuple) =>
          val emptyProductCodec = new ESExprCodecProduct[EmptyTuple] {
            override def encode(value: EmptyTuple): (Seq[ESExpr], Map[String, ESExpr]) =
              (Seq(), Map())

            override def decode(state: ProductDecodeState): Either[ProductDecodeError, (EmptyTuple, ProductDecodeState)] =
              Right((EmptyTuple, state))
          }

          summonInline[ESExprCodecProduct[EmptyTuple] =:= ESExprCodecProduct[Types]](emptyProductCodec)
      end match

    def varargProductCodec[A](varargCodec: VarargCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (varargCodec.encodeVararg(value), Map())

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          varargCodec.decodeVararg(state.args) match {
            case Left((i, DecodeError(message, path))) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex + i, path)))
            case Right(a) => Right((a, state.copy(args = Seq(), positionalIndex = state.positionalIndex + state.args.size)))
          }
      }

    def dictProductCodec[A](dictCodec: DictCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(), dictCodec.encodeDict(value))

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          dictCodec.decodeDict(state.kwargs) match {
            case Left((kw, DecodeError(message, path))) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(kw, path)))
            case Right(a) => Right((a, state.copy(kwargs = Map())))
          }
      }

    def optionalKeywordProductCodec[A](keyword: String, optionalValueCodec: OptionalValueCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          optionalValueCodec.encodeOptional(value) match {
            case Some(expr) => (Seq(), Map(keyword -> expr))
            case _: None.type => (Seq(), Map())
          }

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          optionalValueCodec.decodeOptional(state.kwargs.get(keyword)) match {
            case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
            case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
          }
      }

    def defaultKeywordProductCodec[A](keyword: String, codec: ESExprCodec[A], defaultValue: => A)(using CanEqual[A, A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          if value == defaultValue then
            (Seq(), Map())
          else
            (Seq(), Map(keyword -> codec.encode(value)))
          end if

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.kwargs.get(keyword) match {
            case Some(value) =>
              codec.decode(value) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
                case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
              }

            case None => Right((defaultValue, state))
          }
      }

    def requiredKeywordProductCodec[A](keyword: String, codec: ESExprCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(), Map(keyword -> codec.encode(value)))

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.kwargs
            .get(keyword)
            .toRight { ProductDecodeError(s"Required key $keyword was not provided", ProductErrorPath.Current) }
            .flatMap { value =>
              codec.decode(value) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
                case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
              }
            }
      }

    def optionalPositionalProductCodec[A](optionalValueCodec: OptionalValueCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          optionalValueCodec.encodeOptional(value) match {
            case Some(expr) => (Seq(expr), Map())
            case _: None.type => (Seq(), Map())
          }

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.args match {
            case h +: t =>
              optionalValueCodec.decodeOptional(Some(h)) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state.copy(args = t, positionalIndex = state.positionalIndex + 1)))
              }

            case _ =>
              optionalValueCodec.decodeOptional(None) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state))
              }
          }
      }

    def requiredPositionalProductCodec[A](codec: ESExprCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(codec.encode(value)), Map())

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.args match {
            case h +: t =>
              codec.decode(h) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state.copy(args = t, positionalIndex = state.positionalIndex + 1)))
              }

            case _ => Left(ProductDecodeError("Not enough arguments were provided", ProductErrorPath.Current))
          }
      }

    
    def simpleEnumCodec[T](caseNames: Array[String], caseValues: Map[String, T])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
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

    inline def derivedSumCreateCodec[T](codecMap: => Map[ESExprTag, ESExprCodec[? <: T]])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
      ${ derivedSumMacro[T, m.MirroredElemTypes]('codecMap) }

    def derivedSumMacro[T: Type, SubTypes <: Tuple: Type](codecMap: Expr[Map[ESExprTag, ESExprCodec[? <: T]]])(using q: Quotes): Expr[ESExprCodec[T]] =
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
              .flatMap { codec => codec.decode(expr) }
          end decode
        }
      }
    

    inline def inlineValueCodec[T <: Product, Elem](elemCodec: ESExprCodec[Elem])(using m: Mirror.ProductOf[T] { type MirroredElemTypes = Elem *: EmptyTuple }): ESExprCodec[T] =
      new ESExprCodec[T] {
        override lazy val tags: Set[ESExprTag] = elemCodec.tags

        override def encode(value: T): ESExpr =
          val (elemValue *: EmptyTuple) = Tuple.fromProductTyped(value)
          elemCodec.encode(elemValue)
        end encode

        override def decode(expr: ESExpr): Either[DecodeError, T] =
          elemCodec.decode(expr).map(res =>
            m.fromTuple(res *: EmptyTuple)
          )
      }


    def toSExprName(name: String): String =
      name
        .split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])").nn
        .map(_.nn.toLowerCase)
        .mkString("-")

  end CodecDerivation

}

