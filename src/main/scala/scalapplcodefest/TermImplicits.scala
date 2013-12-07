package scalapplcodefest

import scala.language.implicitConversions
import scalapplcodefest.Math.UnitVec

/**
 * This object provides a set of implicit conversions that allow users
 * to write down terms more compactly.
 *
 * @author Sebastian Riedel
 */
object TermImplicits {

  implicit def intToConstant(x: Int) = Constant(x)
  implicit def intToTerm(x: Int) = RichIntTerm(x)
  implicit def doubleToConstant(x: Double) = Constant(x)
  implicit def booleanToConstant(x: Boolean) = Constant(x)
  implicit def symbolToConstant(x: Symbol) = Constant(x)
  //implicit def predicateToAllAtoms[A,B](p:Predicate[A,B]) = AllGroundAtoms(p)

  implicit def setToConstant[T](x: Set[T]) = Constant(x)
  implicit def setToRichSetTerm[T](x: Set[T]) = RichSetTerm(Constant(x))

  //implicit def funToConstant[A,B](x:Fun[A,B]) = x)
  implicit def toTupleTerm2[T1, T2](tuple: (Term[T1], Term[T2])) = TupleTerm2(tuple._1, tuple._2)
  implicit def toRichTupleTerm2[T1, T2](tuple: (Term[(T1,T2)])) = RichTupleTerm2(tuple)
  implicit def toRichVariable[T](v: Variable[T]) = RichVariable(v)
  implicit def toRichTerm[T](term: Term[T]) = new RichTerm(term)
  implicit def toRichInt[A](i: Term[Int]) = RichIntTerm(i)
  implicit def toRichDouble[A](t: Term[Double]) = RichDoubleTerm(t)
  implicit def toRichBooleanTerm(t: Term[Boolean]) = RichBooleanTerm(t)
  implicit def toRichValueAndGradient(tuple: (Term[(Double,Vector)])) = RichValueAndGradient(tuple)

  implicit def toRichFunTerm[A, B](term: Term[Fun[A, B]]): RichFunTerm[A, B] = term match {
    case f: FunTerm[_, _] => RichFunTerm(f).asInstanceOf[RichFunTerm[A, B]]
    case f => RichFunTerm(FunTerm(f))
  }
  implicit def toRichFunctionSeq[A, B](f: FunTerm[Seq[A], B]) = RichFunctionTermSeq(f)
  implicit def toRichFunction2[A1, A2, B](f: FunTerm[(A1, A2), B]) = RichFunctionTerm2(f)
  implicit def toRichPredicate[A, B](p: Predicate[A, B]) = RichPredicate(p)
  implicit def toRichPredicate2[A1, A2, B](p: Predicate[(A1, A2), B]) = RichPredicate2(p)
  implicit def toRichCartesianProductTerm2[T1,T2](term:CartesianProductTerm2[T1,T2]) = RichCartesianProductTerm2(term)
  implicit def toRichSetTerm[T](s: Term[Set[T]]) = RichSetTerm(s)
  implicit def toRichVec(term: Term[Vector]) = RichVecTerm(term)
  implicit def toRichVarSymbol(symbol: Symbol) = RichVarSymbol(symbol)
  implicit def toRichPredSymbol(symbol: Symbol) = RichPredSymbol(symbol)

  implicit def toFinishedCartesianProduct[A](unfinished: UnfinishedCartesianProduct[A]) = unfinished.finish
  implicit def toRichFinishedCartesianProduct[A](unfinished: UnfinishedCartesianProduct[A]) = RichSetTerm(unfinished.finish)

  case class Assign[T](variable:Variable[T],value:T)

  implicit def toAssign[T](pair:(Variable[T],T)) = Assign(pair._1,pair._2)


  def state(assignments:Assign[_]*) =
    if (assignments.isEmpty) State.empty else State(assignments.map(a => a.variable -> a.value).toMap)

  def funTerm[A,B](f:PartialFunction[A,B]) = Constant(Fun(f, new AllOfType[A], new AllOfType[B]))

  //create bracketed terms
  def br[T](term: Term[T]) = Bracketed(term)

  //table building
  def T[A, B](domain: Term[Set[A]], f: PartialFunction[A, B]) = ConstantFun(Fun.table(domain.eval().get, f))
  def C[T1,T2](a1:Term[Set[T1]], a2:Term[Set[T2]]) = RichCartesianProductTerm2(CartesianProductTerm2(a1,a2))

  //math
  def unit(index: Term[Int], value: Term[Double] = Constant(1.0)) = UnitVec(index, value)
  def I(term: Term[Boolean]) = FunApp(Math.Iverson.Term, term)
  def log(term: Term[Double]) = FunApp(Math.Log.Term, term)


  def dsum(args: Term[Seq[Double]]) = Quantified.DoubleSum(args)
  def dsum(args: Term[Double]*) = Quantified.DoubleSum(SeqTerm(args))
  def vsum(args: Term[Seq[Vector]]) = Quantified.VecSum(args)
  def vsum(args: Term[Vector]*) = Quantified.VecSum(SeqTerm(args))


  implicit def toImageSeq[A, B](f: FunTerm[A, B]) = ImageSeq1(f)
  implicit def toImageSeqCurried2[A1, A2, B](f: FunTerm[A1, Fun[A2, B]]) = ImageSeq2(f)


  implicit def uncurry[A1, A2, R](f: FunTerm[A1, Fun[A2, R]]) = f match {
    case Curried2(uncurried) => uncurried
    case _ => ???
  }

  case class RichVarSymbol(symbol: Symbol) {
    def of[T](set: Term[Set[T]]) = Var(symbol, set)
    def of[T](set: Set[T]) = Var(symbol, Constant(set))
  }

  case class RichState(state: State) {
    def +[T](pair: (Variable[T], T)) = state + SingletonState(pair._1, pair._2)
  }

  case class RichPredSymbol(symbol: Symbol) {
    def of[A, B](domRange: (Term[Set[A]], Term[Set[B]])) = Predicate(symbol, domRange._1, domRange._2)
  }

  case class RichVariable[T](v: Variable[T]) {
    //def ->(value:T) = VarValuePair(v,value)
  }

  case class RichVecTerm(term: Term[Vector]) {
    def dot(that: Term[Vector]) = FunApp(Math.Dot.Term, TupleTerm2(term, that))
    def +(that: Term[Vector]) = FunApp(Math.VecAdd.Term, TupleTerm2(term, that))
    def -(that: Term[Vector]) = FunApp(Math.VecMinus.Term, TupleTerm2(term, that))
  }

  private var anonVarCount = 0
  def freshName() = {
    this.synchronized {
      anonVarCount += 1
    }
    "_x" + anonVarCount
  }

  case class RichSetTerm[T](s: Term[Set[T]], variableName: () => String = () => freshName()) {

    def freshVariable[A](dom: Term[Set[A]] = s) = Var(Symbol(variableName()), dom)

    def as(name: String) = RichSetTerm(s, () => name)

    def map[R](f: Variable[T] => Term[R]): LambdaAbstraction[T, R] = {
      val variable = freshVariable()
      LambdaAbstraction(variable, f(variable))
    }
    def flatMap[A1, A2](f: Variable[T] => LambdaAbstraction[A1, A2]) = {
      val variable: Variable[T] = freshVariable()
      val innerLambda = f(variable)
      LambdaAbstraction(variable, innerLambda)
    }

    def x[T2](that: Term[Set[T2]]) = UnfinishedCartesianProduct2(s, that)
    def x[T2](that: Set[T2]) = UnfinishedCartesianProduct2(s, Constant(that))


    def |->[T2](that: Term[Set[T2]]) = (s, that)

    def mappedBy[A](f:FunTerm[T,A]) =
      FunApp(Dyn(MapIterable,CartesianProductTerm2(s.domain,f.domain),Constant(new AllOfType[Set[A]])),(s,f))

    def collectedBy[A](f:FunTerm[T,A]) =
      FunApp(Dyn(CollectIterable,CartesianProductTerm2(s.domain,f.domain),Constant(new AllOfType[Set[A]])),(s,f))

    def filteredBy[A](f:FunTerm[T,Boolean]) =
      FunApp(Dyn(FilterIterable,CartesianProductTerm2(s.domain,f.domain),Constant(new AllOfType[Set[T]])),(s,f))

  }

  case class RichCartesianProductTerm2[T1, T2](term: CartesianProductTerm2[T1, T2],
                                               variableName1: () => String = () => freshName(),
                                               variableName2: () => String = () => freshName()) {
    def as(name1: String, name2: String) = RichCartesianProductTerm2(term, () => name1, () => name2)

    def map[R](f: ((Variable[T1], Variable[T2])) => Term[R]): LambdaAbstraction[(T1, T2), R] = {
      val variable1 = Var(Symbol(variableName1()), term.a1)
      val variable2 = Var(Symbol(variableName2()), term.a2)
      val applied = f(variable1, variable2)
      //todo: replace variables with arg1 && arg2 of tuple
      val tupleVar = Var(Symbol(s"${variable1.name}_${variable2.name}"),term)
      val arg1 = FunApp(ArgTerm(term,term.a1,0),tupleVar)
      val arg2 = FunApp(ArgTerm(term,term.a1,1),tupleVar)
      val substituted1 = TermConverter.substituteTerm(applied, variable1, arg1)
      val substituted2 = TermConverter.substituteTerm(substituted1, variable2, arg2)
      LambdaAbstraction(tupleVar, substituted2)
    }

    def filter(f: ((Variable[T1], Variable[T2])) => Boolean) = this
    def withFilter(f: ((Variable[T1], Variable[T2])) => Boolean) = this

  }


  trait UnfinishedCartesianProduct[T] {
    def finish: Term[Set[T]]
  }

  case class VarValuePair[T](variable: Variable[T], value: T)

  class RichTerm[T](term: Term[T]) {
    def |(condition: State) = Conditioned(term, condition)
    def |(mappings: (Variable[Any], Any)*) = Conditioned(term, State(mappings.toMap))
    def eval(state: (Variable[Any], Any)*) = term.eval(State(state.toMap))
    def value(state: (Variable[Any], Any)*) = term.value(State(state.toMap))
    def ===(that: Term[T]) = FunApp(new Equals[T].Term, TupleTerm2(term, that))
    def ===(that: T) = FunApp(new Equals[T].Term, TupleTerm2(term, Constant(that)))
    //    def eval(state:VarValuePair[T]*):Option[T] = term.eval(State(state.map(_.toTuple).toMap))

  }

  case class UnfinishedCartesianProduct2[T1, T2](dom1: Term[Set[T1]], dom2: Term[Set[T2]])
    extends UnfinishedCartesianProduct[(T1, T2)] {
    def x[T3](that: Term[Set[T3]]) = UnfinishedCartesianProduct3(dom1, dom2, that)
    def finish = CartesianProductTerm2(dom1, dom2)
  }

  case class UnfinishedCartesianProduct3[T1, T2, T3](dom1: Term[Set[T1]], dom2: Term[Set[T2]], dom3: Term[Set[T3]])
    extends UnfinishedCartesianProduct[(T1, T2, T3)] {
    def finish = CartesianProductTerm3(dom1, dom2, dom3)
  }

  case class RichTupleTerm2[T1,T2](t:Term[(T1,T2)]) {
    def _1 = ArgTerm(t.domain,Constant(new AllOfType[T2]), 0)
    def _2 = ArgTerm(t.domain,Constant(new AllOfType[T2]), 1)
  }

  case class RichValueAndGradient(term:Term[ValueAndGradient]) {
    def +(that: Term[ValueAndGradient]) = FunApp(Math.DoubleAndVectorAdd.Term, TupleTerm2(term,that))
    def -(that: Term[ValueAndGradient]) = FunApp(Math.DoubleAndVectorMinus.Term, TupleTerm2(term,that))
  }

  case class RichIntTerm(i: Term[Int]) {
    def +(that: Term[Int]) = FunApp(Math.IntAdd.Term, TupleTerm2(i, that))
    def -(that: Term[Int]) = FunApp(Math.IntMinus.Term, TupleTerm2(i, that))
    def /(that: Term[Int]) = FunApp(Dyn(IntDivide,CartesianProductTerm2(i.domain,that.domain),Constant(Ints)), TupleTerm2(i, that))

    def ~~(that: Term[Int]) = RangeSet(i, that)
  }

  case class RichDoubleTerm(x: Term[Double]) {
    def +(that: Term[Double]) = FunApp(Math.DoubleAdd.Term, TupleTerm2(x, that))
    def -(that: Term[Double]) = FunApp(Math.DoubleMinus.Term, TupleTerm2(x, that))
    def *(that: Term[Double]) = FunApp(Math.DoubleMultiply.Term, TupleTerm2(x, that))
  }

  case class RichBooleanTerm(x: Term[Boolean]) {
    def &&(that: Term[Boolean]) = FunApp(Logic.And.Term, TupleTerm2(x, that))
    def ||(that: Term[Boolean]) = FunApp(Logic.Or.Term, TupleTerm2(x, that))
    def |=>(that: Term[Boolean]) = FunApp(Logic.Implies.Term, TupleTerm2(x, that))
    def <=>(that: Term[Boolean]) = FunApp(Logic.Equiv.Term, TupleTerm2(x, that))
    def unary_! = FunApp(Logic.Neg.Term, x)
    def unary_$ = FunApp(Math.Iverson.Term, x)
  }


  case class RichFunTerm[A, B](f: FunTerm[A, B]) {
    def apply(a: Term[A]) = FunApp(f, a)
    def isDefined = for (x <- f.funCandidateDom) yield
      FunApp(Dyn(IsDefined,CartesianProductTerm2(f.domain,f.funCandidateDom),Constant(Bools)),TupleTerm2(f,x))

  }

  case class RichFunctionTerm2[A1, A2, B](f: FunTerm[(A1, A2), B]) {
    def apply(a1: Term[A1], a2: Term[A2]) = FunApp(f, TupleTerm2(a1, a2))
  }

  case class RichFunctionTermSeq[A, B](f: FunTerm[Seq[A], B]) {
    def apply[C](args: Term[A]*)(implicit convert: C => Term[A]) = FunApp(f, SeqTerm(args.toSeq))
  }


  case class RichPredicate[A, B](p: Predicate[A, B]) {
    def atom(a: A) = GroundAtom(p, a)
    def allAtoms = AllGroundAtoms(p)
  }

  case class RichPredicate2[A1, A2, B](p: Predicate[(A1, A2), B]) {
    def atom(a1: A1, a2: A2) = GroundAtom(p, (a1, a2))
  }

}