package esexpr

import scala.deriving.Mirror
import scala.quoted.*
import scala.compiletime.{constValue, erasedValue, summonInline}

trait ESExprCodecDerivation[TCodec[_]] {
  import MacroUtils.*

  inline def derived[T](using m: Mirror.Of[T]): TCodec[T] =
    inline m match {
      case m: Mirror.SumOf[T] => derivedSum[T](using m)
      case m: Mirror.ProductOf[T] => derivedProduct[T](using m)
    }


  inline def derivedSum[T](using m: Mirror.SumOf[T]): TCodec[T] =
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

  def simpleEnumCodec[T](caseNames: Array[String], caseValues: Map[String, T])(using m: Mirror.SumOf[T]): TCodec[T]
  def getCodecTags[T](codec: TCodec[T]): Set[ESExprTag]

  inline def buildSumCodecs[T, SubTypes <: Tuple]: Map[ESExprTag, WrappedCodec[TCodec, ? <: T]] =
    inline erasedValue[SubTypes] match
      case _: (htype *: ttypes) =>
        val hcodec =  derived[htype](using summonInline[Mirror.Of[htype]])
        val tailMap = buildSumCodecs[T, ttypes]
        val hcodec2 = summonInline[WrappedCodec[TCodec, htype] <:< WrappedCodec[TCodec, ? <: T]](WrappedCodec(hcodec))

        tailMap ++ getCodecTags(hcodec).toSeq.map(_ -> hcodec2)

      case _: EmptyTuple => Map.empty
    end match

  inline def derivedSumCreateCodec[T](codecMap: => Map[ESExprTag, WrappedCodec[TCodec, ? <: T]])(using m: Mirror.SumOf[T]): TCodec[T]



  type TCodecProduct[T]
  type TCodecField[T]
  type TCodecOptionalField[T] <: TCodecField[T]
  type TCodecDict[T]

  inline def codecProductToCodec[T](using m: Mirror.ProductOf[T])(constructor: String, codecProduct: TCodecProduct[m.MirroredElemTypes]): TCodec[T]

  inline def derivedProduct[T](using m: Mirror.ProductOf[T]): TCodec[T] =
    inline if typeHasAnn[T, inlineValue] then
      derivedInline[T](using m)
    else
      val derivedTuple = derivedProductTuple[T, m.MirroredLabel, m.MirroredElemLabels, m.MirroredElemTypes]
      val constructor =
        inline if typeHasAnn[T, constructor] then
          typeGetAnn[T, constructor].name
        else
          toSExprName(constValue[m.MirroredLabel])

      codecProductToCodec(constructor, derivedTuple)
    end if



  def optionalFieldCodec[Elem](elemCodec: TCodec[Elem]): TCodecField[Option[Elem]]
  def fieldCodecWithDefault[Elem](elemCodec: TCodec[Elem], defaultValue: Elem)(using CanEqual[Elem, Elem]): TCodecField[Elem]
  def codecToFieldCodec[Elem](elemCodec: TCodec[Elem]): TCodecField[Elem]

  def dictProductCodec[Dict, Tail <: Tuple](dictCodec: TCodecDict[Dict], tailCodec: TCodecProduct[Tail]): TCodecProduct[Dict *: Tail]
  def keywordProductCodec[A, Tail <: Tuple](keyName: String, fieldCodec: TCodecField[A], tailCodec: TCodecProduct[Tail]): TCodecProduct[A *: Tail]
  def varargsProductCodec[Elem](typeName: String, elemCodec: TCodec[Elem]): TCodecProduct[Seq[Elem] *: EmptyTuple]
  def positionalProductCodec[Elem, Tail <: Tuple](elemCodec: TCodec[Elem], tailCodec: TCodecProduct[Tail]): TCodecProduct[Elem *: Tail]
  def emptyProductCodec: TCodecProduct[EmptyTuple]

  inline def derivedProductTuple[T, TypeLabel <: String, Labels <: Tuple, Types <: Tuple]: TCodecProduct[Types] =
    inline (erasedValue[Labels], erasedValue[Types]) match
      case _: ((? *: EmptyTuple), (Seq[elem] *: EmptyTuple)) if typeHasVarArgs[T] =>
        lazy val elemCodec = summonInline[TCodec[elem]]
        val codec = varargsProductCodec(constValue[TypeLabel], elemCodec)
        summonInline[TCodecProduct[Seq[elem] *: EmptyTuple] =:= TCodecProduct[Types]](codec)

      case _: ((hlabel *: tlabels), (htype *: ttypes)) if caseFieldHasAnn[T, dict](constValue[hlabel & String]) =>
        lazy val headCodec = summonInline[TCodecDict[htype]]
        val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttypes]
        val codec = dictProductCodec(headCodec, tailCodec)

        summonInline[TCodecProduct[htype *: ttypes] =:= TCodecProduct[Types]](codec)

      case _: ((hlabel *: tlabels), (htype *: ttypes)) if caseFieldHasAnn[T, keyword](constValue[hlabel & String]) =>
        val codec = 
          inline if caseFieldHasAnn[T, optional](constValue[hlabel & String]) then
            deriveProductKeyword[T, TypeLabel, hlabel, tlabels, htype, ttypes](summonInline[TCodecOptionalField[htype]])
          else
            derivedProductTupleRequiredField[T, TypeLabel, htype, ttypes, hlabel, tlabels]

        summonInline[TCodecProduct[htype *: ttypes] =:= TCodecProduct[Types]](codec)

      case _: ((? *: tlabels), (htype *: ttypes)) =>
        lazy val headCodec = summonInline[TCodec[htype]]
        val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttypes]
        val codec = positionalProductCodec(headCodec, tailCodec)

        summonInline[TCodecProduct[htype *: ttypes] =:= TCodecProduct[Types]](codec)

      case _: (EmptyTuple, EmptyTuple) =>
        summonInline[TCodecProduct[EmptyTuple] =:= TCodecProduct[Types]](emptyProductCodec)
    end match

  inline def derivedProductTupleRequiredField[T, TypeLabel <: String, HType, TTypes <: Tuple, HLabel, TLabels <: Tuple]: TCodecProduct[HType *: TTypes] =
    val elemCodec = summonInline[TCodec[HType]]
    lazy val headCodec =
      inline if caseFieldHasDefaultValue[T, HType](constValue[HLabel & String]) then
        val canEqual_HType = summonInline[CanEqual[HType, HType]]
        fieldCodecWithDefault(elemCodec, caseFieldDefaultValue[T, HType](constValue[HLabel & String]))(using canEqual_HType)
      else if caseFieldHasAnn[T, defaultValue[?]](constValue[HLabel & String]) then
        val canEqual_HType = summonInline[CanEqual[HType, HType]]
        fieldCodecWithDefault(elemCodec, caseFieldGetAnn[T, defaultValue[HType]](constValue[HLabel & String]).value)(using canEqual_HType)
      else
        codecToFieldCodec(elemCodec)
      end if


    deriveProductKeyword[T, TypeLabel, HLabel, TLabels, HType, TTypes](headCodec)
  end derivedProductTupleRequiredField

  inline def deriveProductKeyword[T, TypeLabel <: String, HLabel, TLabels <: Tuple, HType, TTypes <: Tuple](headCodec: TCodecField[HType]): TCodecProduct[HType *: TTypes] =
    val keyName =
      val name = caseFieldGetAnn[T, keyword](constValue[HLabel & String]).name
      if name.isEmpty then
        toSExprName(constValue[HLabel & String])
      else
        name
    end keyName

    val tailCodec = derivedProductTuple[T, TypeLabel, TLabels, TTypes]
    keywordProductCodec(keyName, headCodec, tailCodec)
  end deriveProductKeyword


  def inlineCodec[T <: Product, Elem](elemCodec: TCodec[Elem])(using m: Mirror.ProductOf[T] { type MirroredElemTypes = Elem *: EmptyTuple }): TCodec[T]

  inline def derivedInline[T](using m: Mirror.ProductOf[T]): TCodec[T] =
    inline erasedValue[m.MirroredElemTypes] match {
      case _: (elem *: EmptyTuple) =>
        lazy val elemCodec = summonInline[TCodec[elem]]

        type InnerMirror = Mirror.ProductOf[T & Product] { type MirroredElemTypes = elem *: EmptyTuple }

        val codec = inlineCodec[T & Product, elem](elemCodec)(using summonInline[m.type <:< InnerMirror](m))
        summonInline[TCodec[T & Product] =:= TCodec[T]](codec)
    }

  def toSExprName(name: String): String =
    name
      .split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])").nn
      .map(_.nn.toLowerCase)
      .mkString("-")

}

