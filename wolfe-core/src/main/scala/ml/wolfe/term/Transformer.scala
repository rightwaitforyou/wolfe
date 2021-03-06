package ml.wolfe.term

import com.typesafe.scalalogging.slf4j.LazyLogging
import ml.wolfe.util.ObjectId

/**
 * @author riedel
 */
object Transformer extends LazyLogging {

  import TermImplicits._

  def depthFirst(term: AnyTerm)(partialFunction: PartialFunction[AnyTerm, AnyTerm]): AnyTerm = {
    val result = term match {
      case n: NAry =>
        val transformed = n.arguments map ((t: AnyTerm) => depthFirst(t)(partialFunction).asInstanceOf[n.ArgumentType])
        val copied = n.copyIfDifferent(transformed)
        copied
      case t => t
    }
    val transformed = if (partialFunction.isDefinedAt(result)) partialFunction(result) else result
    transformed
  }

  def depthFirstAndReuse(term: AnyTerm, mapping: Map[ObjectId[AnyTerm], AnyTerm] = Map.empty)
                        (partialFunction: PartialFunction[AnyTerm, AnyTerm]): (AnyTerm, Map[ObjectId[AnyTerm], AnyTerm]) = {
    val termId = new ObjectId(term)
    mapping.get(termId) match {
      case Some(x) =>
        (x, mapping)
      case None =>
        val (result, newMap) = term match {
          case n: NAry =>
            val (mappings, arguments) = n.arguments.foldLeft((mapping, IndexedSeq.empty[n.ArgumentType])) {
              case ((map, args), arg) =>
                val (t, m) = depthFirstAndReuse(arg, map)(partialFunction)
                (map ++ m, args :+ t.asInstanceOf[n.ArgumentType])
            }
            val copied = n.copyIfDifferent(arguments)
            (copied, mappings)
          case t =>
            (t, mapping)
        }
        val transformed = if (partialFunction.isDefinedAt(result)) partialFunction(result) else result
        (transformed, newMap + (termId -> transformed))
    }
  }

  def depthLastAndReuse(term: AnyTerm, mapping: Map[ObjectId[AnyTerm], AnyTerm] = Map.empty)
                       (partialFunction: PartialFunction[AnyTerm, AnyTerm]): (AnyTerm, Map[ObjectId[AnyTerm], AnyTerm]) = {
    val termId = new ObjectId(term)
    mapping.get(termId) match {
      case Some(x) =>
        (x, mapping)
      case None =>
        if (partialFunction.isDefinedAt(term)) {
          val result = partialFunction(term)
          (result, mapping + (termId -> result))
        } else {
          term match {
            case n: NAry =>
              val (mappings, arguments) = n.arguments.foldLeft((mapping, IndexedSeq.empty[n.ArgumentType])) {
                case ((map, args), arg) =>
                  val (t, m) = depthLastAndReuse(arg, map)(partialFunction)
                  (map ++ m, args :+ t.asInstanceOf[n.ArgumentType])
              }
              val copied = n.copyIfDifferent(arguments)
              (copied, mappings + (termId -> copied))
            case t =>
              (t, mapping)
          }
        }
    }
  }


  def depthLast(term: AnyTerm)(partialFunction: PartialFunction[AnyTerm, AnyTerm]): AnyTerm = {
    if (partialFunction.isDefinedAt(term)) partialFunction(term)
    else term match {
      case n: NAry =>
        val transformed = n.arguments map ((t: AnyTerm) => depthLast(t)(partialFunction).asInstanceOf[n.ArgumentType])
        val copied = n.copyIfDifferent(transformed)
        copied
      case t =>
        t
    }
  }

  def replace[D <: Dom](term: AnyTerm)(variable: Var[D], value: Term[D]): AnyTerm = {
    depthLastAndReuse(term) {
      case t if !t.vars.contains(variable) =>
        t
      case t: Memoized[_, _] =>
        Substituted(t, variable, value)
      case `variable` =>
        value
    }._1
  }

  /**
   * Removes "OwnedTerms" that are mostly just used for type safety on the client side.
   * @param term term to clean
   * @return term without owned terms.
   */
  def clean(term: AnyTerm) = depthFirstAndReuse(term) {
    case o: OwnedTerm[_] if !o.keep =>
      o.self
  }._1

  def flattenSums(term: AnyTerm) = {
    logger.info("Flattening sums")
    depthFirstAndReuse(term) {
      case Sum(args) => Sum(args flatMap {
        case Sum(inner) =>
          inner
        case a =>
          IndexedSeq(a)
      })
    }._1
  }

  def atomizeVariables(toGround: Seq[AnyVar])(term: AnyTerm) = depthFirstAndReuse(term) {
    case v: Var[_] if toGround.contains(v) => VarAtom(v)
    case VarSeqApply(a: Atom[_], i) => SeqAtom[Dom, VarSeqDom[Dom]](a.asInstanceOf[Atom[VarSeqDom[Dom]]], i)
    case VarSeqLength(a: Atom[_]) => LengthAtom[VarSeqDom[Dom]](a.asInstanceOf[Atom[VarSeqDom[Dom]]])
    case f@Field(product:Atom[_],dom,offsets) => FieldAtom(product,dom,offsets,f.fieldName)
  }._1

  /**
   * Creates more fine grained atoms for each atom that still has internal structure.
   * @param term the term to shatter
   * @return a term where each atom with a structured domain is replaced by a term of the same shape
   *         that constructs the structure using finer grained atoms.
   */
  def shatterAtoms(term: AnyTerm):AnyTerm = depthLastAndReuse(term) {
    case a:Atom[_] => a.domain match {
      case d:VarSeqDom[Dom] =>
        //atom has more structure, create new atoms
        val parentAtom = a.asInstanceOf[Atom[VarSeqDom[Dom]]]
        val elements = Range(0, d.maxLength) map
          (i => shatterAtoms(SeqAtom[Dom, VarSeqDom[Dom]](parentAtom, d.indexDom.Const(i))))
        val lengthAtom = LengthAtom(parentAtom)
        val shattered = new VarSeqConstructor[Dom,VarSeqDom[Dom]](lengthAtom,elements,d)
        shattered
      case _ => a
    } //todo: deal with maps

  }._1


  def groundSums(term: AnyTerm):AnyTerm = depthFirst(term) {
    case FirstOrderDoubleSum(indices, variable, body) =>
      val length = indices match {
        case i: VarSeqDom[_]#Term => i.length
        case i => VarSeqLength(indices)
      }
      val doubleTerms = for (i <- 0 until indices.domain.maxLength) yield {
        val element = indices match {
          case v: VarSeqDom[_]#Term => v.elementAt(i)
          case v => new VarSeqApply[Dom, Term[VarSeqDom[Dom]], IntTerm](v, i.toConst)
        }
        val doubleTerm = depthFirst(body) {
          case t if t == variable =>
            element
        }
        //(body | variable << element).asInstanceOf[DoubleTerm]
        val grounded = replace(body)(variable, element).asInstanceOf[DoubleTerm]
        val recursed = groundSums(grounded).asInstanceOf[DoubleTerm]
        recursed
        //doubleTerm.asInstanceOf[DoubleTerm]
      }
      val sumArgs = VarSeq(length, doubleTerms)
      sum(doubleTerms, length)
    //varSeqSum[Term[VarSeqDom[TypedDom[Double]]]](sumArgs)
  }

  def precalculate(term: AnyTerm) = {
    logger.info("Pre-calculation of terms")
    depthLastAndReuse(term) {
      case t if t.isStatic && !t.isInstanceOf[Precalculated[_]] =>
        t.domain.own(Precalculated(t).asInstanceOf[TypedTerm[t.domain.Value]])
    }._1
  }

}

object Traversal {


  trait UniqueSampleTerm {
    def term: SampleTerm
  }

  case class Alone(term: SampleTerm) extends UniqueSampleTerm

  case class InMem(term: SampleTerm, mem: Mem) extends UniqueSampleTerm

  def uniqueSampleTerms(term: AnyTerm): List[UniqueSampleTerm] = {
    term match {
      case m: Memoized[_, _] =>
        val children = uniqueSampleTerms(m.term)
        for (c <- children) yield c match {
          case Alone(t) => InMem(t, m.asInstanceOf[Mem])
          case t => t
        }
      case s: SampleTerm => Alone(s) :: Nil
      case n: NAry => n.arguments.toList.flatMap(a => uniqueSampleTerms(a)).distinct
      case _ => Nil
    }
  }

  def distinctSampleCount(term: AnyTerm) = uniqueSampleTerms(term).map(_.term.domain.domainSize).product


}