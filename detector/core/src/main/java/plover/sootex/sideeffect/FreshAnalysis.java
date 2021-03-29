package plover.sootex.sideeffect;

import plover.soot.Cache;
import plover.soot.SootUtils;
import plover.soot.Utils;
import plover.soot.callgraph.Callees;
import plover.soot.graph.GraphHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.typing.fast.BottomType;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.HashMutableDirectedGraph;
import soot.toolkits.graph.MutableDirectedGraph;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;

import java.util.*;

/**
 * Implement the fresh method and variable analysis provided by Gay@CC 2000
 * A fresh method return a newly created objects (not exist before method call). 
 * NOTE that the object may already bean escaped or also return from parameters.
 *
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class FreshAnalysis {

	public static final Logger LOGGER = LoggerFactory.getLogger(FreshAnalysis.class);
	/**
	 * returned Locals of methods
	 */
	protected Set<Local>[] returnedVars;
	/**
	 * indicate if all return values of method are fresh,
	 * note that we filter out this object and formal parameters in return values,
	 * because freshMethod is used to determine the freshness of left operands in caller,
	 * and this object and formal parameters in callee might not be non-fresh in caller.
	 */
	protected boolean[] freshMethod;
	/**
	 * non fresh locals,
	 * note that this object and formal parameters are non-fresh locals inherently
	 */
	protected Set<Local>[] nonFreshVars;
	
	protected CallGraph cg;

	protected MustAliasIdentityLocalsQuery mustAliasQuery;

	public FreshAnalysis(CallGraph cg) {
		this.cg = cg;
	}

	public FreshAnalysis(CallGraph cg, MustAliasIdentityLocalsQuery mustAliasQuery){
		this.cg = cg;
		this.mustAliasQuery = mustAliasQuery;
	}
	
	protected MutableDirectedGraph<Object> initVarConnectionGraph(SootMethod m){
		// build a variable connection graph
		HashMutableDirectedGraph varConn = new HashMutableDirectedGraph();
		Body body = m.retrieveActiveBody();
		
		varConn.addNode(Boolean.TRUE);
		varConn.addNode(Boolean.FALSE);

		Chain<Local> allLocals = body.getLocals();
		for (Local l : allLocals){
			Type t = l.getType();
			if(t instanceof RefLikeType){
				varConn.addNode(l);
			}
			else if(t instanceof BottomType){
				varConn.addNode(l);
			}
		}

		return varConn;
	}

	/**
	 * Add constrains caused by dependencies between out (return value) to in parameters (actual parameters).
	 * More specifically, here are some callee whose return value has data-dependency on its actual parameters.
	 * Therefore, the return value of the caller has data-dependency on its formal parameters.
	 * @param varConn
	 * @param tgt
	 * @param returned
	 * @param left
	 * @param right
	 */
	protected void addParamInOutConstraints(MutableDirectedGraph<Object> varConn, SootMethod tgt, Set<Local> returned,
											Value left, Value right, boolean leftToRight) {
		if (returned == null) {
			// FIXME return should not be null
			return;
		}

		if (returned.isEmpty()) {
			return;
		}

		Body body = tgt.retrieveActiveBody();
		// static methods do not have this local
		if (!tgt.isStatic()) {
			// return values contain ThisLocal, we connect left operands with the base of right operands
			Local thiz = body.getThisLocal();
			if (returned.contains(thiz)) {
				if (leftToRight) {
					varConn.addEdge(left, ((InstanceInvokeExpr) right).getBase());
				} else {
					varConn.addEdge(((InstanceInvokeExpr) right).getBase(), left);
				}
			}
		}
		// connect left operands with actual parameters according to the formal parameters
		int pc = tgt.getParameterCount();
		InvokeExpr invoke = (InvokeExpr) right;
		for (int i = 0; i < pc; i++) {
			Local p = body.getParameterLocal(i);
			if (returned.contains(p)) {
				Value lv = invoke.getArg(i);
				if (lv instanceof Local) {
					if (leftToRight) {
						varConn.addEdge(left, lv);
					} else {
						varConn.addEdge(lv, left);
					}
				}
			}
		}
	}

	/**
	 * @param m
	 */
	protected void returnedAnalysis(SootMethod m) {
		// build a variable connection graph
		MutableDirectedGraph<Object> varConn = initVarConnectionGraph(m);
		Body body = m.retrieveActiveBody();
		MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(m);

		for (Unit u : body.getUnits()) {
			if (u instanceof ReturnStmt) {
				Value v = ((ReturnStmt) u).getOp();
				if (v.getType() instanceof RefLikeType && v instanceof Local) {
					varConn.addEdge(Boolean.TRUE, v);
				}
			}  else if (u instanceof DefinitionStmt) {
				DefinitionStmt d = (DefinitionStmt) u;
				Value left = d.getLeftOp();
				Value right = d.getRightOp();

				// assignment on non-reference variables
				if (!(left.getType() instanceof RefLikeType)) {
					// do nothing
				} else if (left instanceof Local) {
					if (right instanceof Local) {
						varConn.addEdge(left, right);
					}
					// l = r.f
					else if(right instanceof InstanceFieldRef){
						Value base =  ((InstanceFieldRef) right).getBase();
						if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
							varConn.addEdge(left, base);
						}
					}
					// l = r[]
					else if(right instanceof ArrayRef){
						Value base =  ((ArrayRef) right).getBase();
						if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
							varConn.addEdge(left, base);
						}
					} if (right instanceof CastExpr) {
						CastExpr cast = (CastExpr) right;
						Value op = cast.getOp();
						if (op instanceof Local) {
							varConn.addEdge(left, op);
						}// 2012.2.15 tao
					} else if (right instanceof PhiExpr) {
						List<Value> arg=((PhiExpr) right).getValues();

						for(Value value:arg){
							varConn.addEdge(left, value);
						}
						//throw new RuntimeException("PhiExpr processing has not implemented");
					} else if (right instanceof InvokeExpr) {
						Callees callees = new Callees(cg, u);
						for (SootMethod tgt : callees.explicits()) {
							Set<Local> returned = returnedVars[tgt.getNumber()];
							// if called method not analyzed (recursive or native methods), use worst assumption
							// We assume left operands depend on all parameters of right.
							// This branch should not happen ideally because we traverse method with
							// topological order and the callee methods should be visited before
							// the caller methods. However, there are some special cases,
							// such as recursion, will lead to this branch.
							if (returned == null) {
								// what if tgt is static?
								if (!tgt.isStatic()) {
									varConn.addEdge(left, ((InstanceInvokeExpr) right).getBase());
								}
								int pc = tgt.getParameterCount();
								InvokeExpr invoke = (InvokeExpr) right;
								for (int i = 0; i < pc; i++) {
									Value lv = invoke.getArg(i);
									if (lv instanceof Local && lv.getType() instanceof RefLikeType) {
										varConn.addEdge(left, lv);
									}
								}
								break; // break here as we use the worst case
							} else {
								addParamInOutConstraints(varConn, tgt,
										returned, left, right, true);
							}
						}
					}
				}
			}
		}

		// solve the constraints
		Set returned = GraphHelper.getReachables(varConn, Boolean.TRUE);
		returnedVars[m.getNumber()] = returned;
	}


	/**
	 * It is hard to get all fresh variables directly, because we need to check all definitions of variables
	 * are fresh. Therefore, we choose to get all non-fresh variables, which is the the complementary problem
	 * of our previous problem.
	 * @param m
	 */
	protected void freshAnalysis(SootMethod m){
		Set<Local> returns = new HashSet<Local>();
		Set<Pair<Object, Object>> removeEdges = new HashSet<>();
		
		// build a constraint graph
		MutableDirectedGraph<Object> varConn = initVarConnectionGraph(m);
		Body body = m.retrieveActiveBody();
		MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(m);

		for(Unit u: body.getUnits()){
			if(u instanceof ReturnStmt){
				Value v = ((ReturnStmt)u).getOp();
				if(v.getType() instanceof RefLikeType && v instanceof Local){
					returns.add((Local)v);
				}
			}
			else if(u instanceof DefinitionStmt){
				DefinitionStmt d = (DefinitionStmt)u;
				Value left =  d.getLeftOp();
				Value right = d.getRightOp();
				
				Type leftType = left.getType();
				
				// assignment on non-reference variables
				if(!(leftType instanceof RefLikeType) && !(leftType instanceof BottomType)){
					
				}							
				else if(left instanceof Local){
					// 1. l = @param, l = @this
					// Conceptually, formal parameters and this object are not fresh locals,
					// as they are created at outside of current function
					if(d instanceof IdentityStmt){
						varConn.addNode(right);
						varConn.addEdge(left, right);
						varConn.addEdge(Boolean.FALSE, right);
						removeEdges.add(new Pair<>(Boolean.FALSE, right));
					}
					// 2. "l = new C", "l = constant"
					// Obviously, the returned object of new expression are fresh
					else if(right instanceof AnyNewExpr || right instanceof Constant){}
					// 3. l = r
					// The freshness of l is determined by r
					else if(right instanceof Local){
						varConn.addEdge(right, left);
					}
					// 4. l = (cast)r
					// The freshness of l is determined by r
					else if(right instanceof CastExpr){
						CastExpr cast = (CastExpr)right;
						Value op = cast.getOp();
						if(op instanceof Local){
							varConn.addEdge(op, left);
						}
					}		
					// 5. l = r.f
					// TODO can we add connection between l and r, instead of connecting l and Boolean.FALSE?
					else if(right instanceof InstanceFieldRef){
						Value base =  ((InstanceFieldRef) right).getBase();
						if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
							varConn.addEdge(base, left);
						} else {
							varConn.addEdge(Boolean.FALSE, left);
						}
					}
					// 6. l = r[]
					// TODO can we add connection between l and r, instead of connecting l and Boolean.FALSE?
					else if(right instanceof ArrayRef){
						Value base =  ((ArrayRef) right).getBase();
						if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
							varConn.addEdge(base, left);
						} else {
							// TODO too conservative
//							varConn.addEdge(Boolean.FALSE, left);
						}
					}
					// 7. l = g
					// local l points to static field g, static fields are created at outside of current function,
					// so local l is not fresh
					else if(right instanceof StaticFieldRef){
						varConn.addEdge(Boolean.FALSE, left);
					}
					// 8. l = Phi();
					else if (right instanceof PhiExpr) {
						List<Value> args = ((PhiExpr) right).getValues();
						for(Value a: args){
							varConn.addEdge(a, left);
							//varConn.addEdge(left, value);
						}
					} 
					else if(right instanceof InvokeExpr){
						Callees callees = new Callees(cg, u);
						boolean allFresh = true;
						for(SootMethod tgt: callees.explicits()){
							if (tgt.isNative()) {
								continue;
							}
							if(!isFreshMethod(tgt)){
								allFresh = false;
							}
							// no matter whether the method is fresh,
							// we will connect left operands and right operands (actual parameters)
							Set<Local> returned = returnedVars[tgt.getNumber()];
							addParamInOutConstraints(varConn, tgt, returned, left, right, false);
						}
						if(!allFresh){
							varConn.addEdge(Boolean.FALSE, left);
						}
					}
					else{
						if(!(leftType instanceof BottomType)){
						//	throw new RuntimeException();
						}
						else{
							// not sure when this code will be executed
							System.out.println("BottomType unit: "+u);
						}
					}
				}
				// 6. g = r
				// The freshness of r will not be changed by this case
				else if(left instanceof StaticFieldRef){}
				// 7. l.f = r
				// The freshness of r will not be changed by this case
				else if(left instanceof InstanceFieldRef){}
				// 9. l[] = r
				// The freshness of r will not be changed by this case
				else if(left instanceof ArrayRef){}
				//others
				else{
					throw new RuntimeException();
				}
			}
		}
		
		// XXX solve constraints
		Set nonfresh = GraphHelper.getReachables(varConn, Boolean.FALSE);
		nonFreshVars[m.getNumber()] = nonfresh;
		// freshMethod is used by callers to check if all return values of callee are fresh.
		// However, some non-fresh operands in callee, including this object and formal parameters, might be fresh
		// remove all edges of l = @param and l = @this
		for (Pair edge : removeEdges) {
			varConn.removeEdge(edge.getO1(), edge.getO2());
		}
		Set nonfreshAfterRemovingEdge = GraphHelper.getReachables(varConn, Boolean.FALSE);
		boolean rfresh = true;
		for (Local r : returns) {
			if (nonfreshAfterRemovingEdge.contains(r)) {
				rfresh = false;
				break;
			}
		}
		freshMethod[m.getNumber()] = rfresh;
	}
	
	
	public void build() {
		Date startTime = new Date();
		
		freshMethod = new boolean[SootUtils.getMethodCount()];
		nonFreshVars = new Set[SootUtils.getMethodCount()];
		returnedVars = new Set[SootUtils.getMethodCount()];

		// alias analysis
		if (mustAliasQuery == null) {
			mustAliasQuery = new MustAliasIdentityLocalsQuery();
			mustAliasQuery.build();
		}
		
		List<?> rm = Cache.v().getReverseTopologicalOrder();
		for (Iterator<?> it = rm.iterator(); it.hasNext();) {
			SootMethod sootMethod = (SootMethod) it.next();
			if(sootMethod.isConcrete()){

				boolean hasRefReturn = sootMethod.getReturnType() instanceof RefLikeType;
				if (hasRefReturn) {
					returnedAnalysis(sootMethod);
				} else {
					returnedVars[sootMethod.getNumber()] = Collections.EMPTY_SET;
				}
				freshAnalysis(sootMethod);
			}
		}

		cg = null;
		Date endTime = new Date();
		LOGGER.info("[FreshAnalysis] {}  methods analyzed in {}", rm.size(), Utils.getTimeConsumed(startTime, endTime));
	}
	
	public boolean isFreshMethod(SootMethod m){
		return freshMethod[m.getNumber()];
	}

	public boolean isRefTgtFresh(SootMethod m, Local v) {
		Set<Local> nonfresh = nonFreshVars[m.getNumber()];
		if(nonfresh!=null){
			return !nonfresh.contains(v);
		}
		else{
			return false;
		}
	}

}
