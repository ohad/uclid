/*
 * UCLID5 Verification and Synthesis Engine
 *
 * Copyright (c) 2017. The Regents of the University of California (Regents).
 * All Rights Reserved.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for educational, research, and not-for-profit purposes,
 * without fee and without a signed licensing agreement, is hereby granted,
 * provided that the above copyright notice, this paragraph and the following two
 * paragraphs appear in all copies, modifications, and distributions.
 *
 * Contact The Office of Technology Licensing, UC Berkeley, 2150 Shattuck Avenue,
 * Suite 510, Berkeley, CA 94720-1620, (510) 643-7201, otl@berkeley.edu,
 * http://ipira.berkeley.edu/industry-info for commercial licensing opportunities.
 *
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF
 * THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF REGENTS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS
 * PROVIDED "AS IS". REGENTS HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT,
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Pramod Subramanyan

 * Class that manages proof obligations generated by UCLID5.
 *
 */

package uclid

import uclid.lang._

import scala.collection.mutable.ListBuffer

case class AssertInfo(name : String, label : String, frameTable : SymbolicSimulator.FrameTable, context : Scope, iter : Int, expr : smt.Expr, pos : ASTPosition) {
  override def toString = {
    label + " [Step #" + iter.toString + "] " + name + " @ " + pos.toString
  }
}

case class CheckResult(assert : AssertInfo, result : smt.SolverResult)

class AssertionTree {
  class TreeNode(p : Option[TreeNode], assumps : List[smt.Expr]) {
    var parent : Option[TreeNode] = p
    var children : ListBuffer[TreeNode] = ListBuffer.empty
    var assumptions: ListBuffer[smt.Expr] = assumps.to[ListBuffer]
    var assertions: ListBuffer[AssertInfo] = ListBuffer.empty
    var results : List[CheckResult] = List.empty

    // these two functions add assumptions.
    def +=(e : smt.Expr) { assumptions += e }
    // and these two add assertions
    def +=(assert: AssertInfo) { assertions += assert }
  }

  val root : TreeNode = new TreeNode(None, List.empty)
  val initialRoot : TreeNode = root
  var currentNode : TreeNode = root

  def addAssumption(assump: smt.Expr) {
    if (currentNode.assertions.size > 0) {
      val childNode = new TreeNode(Some(currentNode), List(assump))
      currentNode.children += childNode
      currentNode = childNode
    } else {
      currentNode += assump
    }
  }

  def addAssert(assert: AssertInfo) {
    currentNode += assert
  }

  def resetToInitial() {
    currentNode = initialRoot
  }

  def _verify(node : TreeNode, solver : smt.SolverInterface) : List[CheckResult] = {
    solver.addAssumptions(node.assumptions.toList)
    node.results = (node.assertions.map {
      e => {
        val sat = solver.check(smt.OperatorApplication(smt.NegationOp, List(e.expr)))
        val result = sat.result match {
          case Some(true)  => smt.SolverResult(Some(false), sat.model)
          case Some(false) => smt.SolverResult(Some(true), sat.model)
          case None        => smt.SolverResult(None, None)
        }
        CheckResult(e, result)
      }
    }).toList
    // now recurse into children
    val childResults = node.children.flatMap(c => _verify(c, solver))
    solver.popAssumptions()
    node.results ++ childResults
  }
  def verify(solver : smt.SolverInterface) : List[CheckResult] = _verify(root, solver)
}