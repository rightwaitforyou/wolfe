package ml.wolfe.term

/**
 * @author riedel
 */
case class Substituted[D <: Dom, V <: Dom](term:Term[D],variable:Var[V],value:Term[V]) extends NAry with Term[D] {

  val vars = (term.vars.filterNot(_ == variable) ++ value.vars).distinct
  val domain = term.domain

  lazy val substituted = Transformer.depthFirst(term) {
    case v:Var[_] if v == variable => value
  }

  //lazy val isStatic = substituted.isStatic

  type ArgumentType = Term[Dom]
  val arguments = IndexedSeq(term,value)

  val indexOfVar = term.vars.indexOf(variable)

  override def evaluatorImpl(in: Settings) = new AbstractEvaluator(in) {
    val innerInput = in.linkedSettings(vars,term.vars)
    innerInput(indexOfVar) = variable.domain.createSetting()
    val valueInput = in.linkedSettings(vars,value.vars)
    val innerEval = term.evaluatorImpl(innerInput)
    val valueEval = value.evaluatorImpl(valueInput)

    def eval()(implicit execution: Execution) = {
      valueEval.eval()
      innerInput(indexOfVar) shallowAssign valueEval.output
      innerEval.eval()
    }

    val output = innerEval.output
  }


  override def toString = s"($term)|$variable << $value"

  def copy(args: IndexedSeq[ArgumentType]) = Substituted[D,V](args(0).asInstanceOf[Term[D]],variable,value.asInstanceOf[Term[V]])


}
