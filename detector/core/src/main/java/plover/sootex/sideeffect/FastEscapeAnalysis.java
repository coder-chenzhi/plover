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
import soot.shimple.PhiExpr;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.MutableDirectedGraph;
import soot.toolkits.scalar.Pair;

import java.util.*;

/**
 * Implement the fresh method and variable analysis provided by Gay@CC 2000 A
 * fresh method return a newly created objects (not exist before method call).
 * NOTE that the object may already bean escaped or also return from parameters.
 *
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class FastEscapeAnalysis extends FreshAnalysis implements ILocalityQuery{

	public static final Logger LOGGER = LoggerFactory.getLogger(FastEscapeAnalysis.class);

	/**
	 * escaped Locals of methods when this object and formal parameters are taken as escaped sink
	 */
	protected Set<Local>[] escapedVars;

	/**
	 * escaped Locals of methods when this object and formal parameters are not taken as escaped sink
	 * this is the subset of escapedVars, and is used to determine whether the actual parameters of the caller have escaped in callee
	 */
	protected Set<Local>[] realEscapedVars;

	/**
	 * a symbol that indicate whether any return values of method has been escaped,
	 * and is used to determine whether the return value of the caller have escaped in callee
	 * note that this object and formal parameters are not taken as escaped sink
	 */
	protected boolean[] escapedMethod;

	protected List<String> knownSkippableMethods;

	public FastEscapeAnalysis(CallGraph cg) {
		super(cg);
	}

	public FastEscapeAnalysis(CallGraph cg, MustAliasIdentityLocalsQuery mustAliasQuery){
		super(cg, mustAliasQuery);
		initKnownSkippableMethods();
	}

	private void initKnownSkippableMethods() {
		knownSkippableMethods = new ArrayList<String>(
				Arrays.asList(
						"append(java.lang.String)",
						"java.lang.String format",
						"void debug", // debug logging will escape all parameters for some reasons
						"void trace" // trace logging will escape all parameters for some reasons
				)
		);

	}


	private void handleMethodCallForEscape(MutableDirectedGraph<Object> varConn,
                                           Value left, Value right, Unit call) {
		Callees callees = new Callees(cg, call);
		for (SootMethod tgt : callees.explicits()) {
			boolean skip = false;
			for (String skipMethod : knownSkippableMethods) {
				if (tgt.toString().contains(skipMethod)) {
					skip = true;
					break;
				}
			}
			if (skip) {
				continue;
			}

			int tgtId = tgt.getNumber();
			Set<Local> realEscaped = realEscapedVars[tgtId];
			// if callee not analyzed yet, use worst assumptions
			// We assume all are escaped
			if (realEscaped == null) {
				// skip if native method
				if (tgt.isNative()) {
					continue;
				}

				LOGGER.debug("Worst case for method {}", tgt.getSignature());
				// return value itself
				if (left != null) {
					varConn.addEdge(Boolean.TRUE, left);
				}

				// receiver
				if (!tgt.isStatic()) {
					varConn.addEdge(Boolean.TRUE,((InstanceInvokeExpr) right).getBase());
				}

				// arguments
				int pc = tgt.getParameterCount();
				InvokeExpr invoke = (InvokeExpr) right;
				for (int i = 0; i < pc; i++) {
					Value lv = invoke.getArg(i);
					if (lv instanceof Local && lv.getType() instanceof RefLikeType) {
						varConn.addEdge(Boolean.TRUE, lv);
					}
				}
				break;
			} else {
				// connect return value and actual parameters
				if (left != null) {
					Set<Local> returned = returnedVars[tgtId];
					addParamInOutConstraints(varConn, tgt, returned, left, right, true);
				}

				// if any of the return values of callee is escaped,
				// the left operand of caller also is escaped
				if (left != null && escapedMethod[tgtId]) {
					varConn.addEdge(Boolean.TRUE, left);
				}

				// if this object is escaped in the callee,
				// the receiver object of caller is also escaped
				Body body = tgt.retrieveActiveBody();
				if (!tgt.isStatic()) {
					Local thiz = body.getThisLocal();
					if (realEscaped.contains(thiz)) {
						varConn.addEdge(Boolean.TRUE,
								((InstanceInvokeExpr) right).getBase());
					}
				}

				// if the formal parameters are escaped in the callee,
				// the corresponding actual parameters are also escaped
				int pc = tgt.getParameterCount();
				InvokeExpr invoke = (InvokeExpr) right;
				for (int i = 0; i < pc; i++) {
					Local p = body.getParameterLocal(i);
					if (realEscaped.contains(p)) {
						Value lv = invoke.getArg(i);
						if (lv instanceof Local
								&& lv.getType() instanceof RefLikeType) {
							varConn.addEdge(Boolean.TRUE, lv);
						}
					}
				}
			}
		}

		// TODO: Ignore implicit method calls here
	}

	protected void escapedAnalysis(SootMethod m) {
//		if (m.toString().equals("<org.apache.zookeeper.util.SecurityUtils$1: javax.security.sasl.SaslClient run()>")) {
//			System.out.println("Pause");
//		}

		Set<Local> returns = new HashSet<Local>();
		Set<Pair<Object, Object>> removeEdges = new HashSet<>();

		// build a constraint graph
		MutableDirectedGraph<Object> varConn = initVarConnectionGraph(m);
		Body body = m.retrieveActiveBody();
		MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(m);

		
		for (Unit u : body.getUnits()) {
			if (u instanceof ReturnStmt) {
				Value v = ((ReturnStmt) u).getOp();
				if (v.getType() instanceof RefLikeType && v instanceof Local) {
					returns.add((Local) v);
				}
			}  else if (u instanceof ThrowStmt) {
				Value t = ((ThrowStmt) u).getOp();
				varConn.addEdge(Boolean.TRUE, t);
			} else if (u instanceof DefinitionStmt) {
				DefinitionStmt d = (DefinitionStmt) u;
				Value left = d.getLeftOp();
				Value right = d.getRightOp();

				if (!(left.getType() instanceof RefLikeType)) {
					// assignment on non-reference variables, do nothing
				} else if (left instanceof Local) {
					// 1. l = @param, l = @this
					if (d instanceof IdentityStmt) {
						varConn.addNode(right);
						varConn.addEdge(right, left);
						varConn.addEdge(Boolean.TRUE, right);
						removeEdges.add(new Pair<>(Boolean.TRUE, right));
					}
					// 2. "l = new C", "l = constant"
					else if (right instanceof AnyNewExpr
							|| right instanceof Constant) {
					}
					// 3. l = r
					else if (right instanceof Local) {
						varConn.addEdge(left, right);
					}
					// 4. l = (cast)r
					else if (right instanceof CastExpr) {
						CastExpr cast = (CastExpr) right;
						Value op = cast.getOp();
						if (op instanceof Local) {
							varConn.addEdge(left, op);
						}
					}
					// 5. l = r.f, l = r[], l = g
					else if (right instanceof ConcreteRef) {
						// The escape status of l or r will not be changed by those operations
					} else if (right instanceof InvokeExpr) {
						handleMethodCallForEscape(varConn, left, right, u);
					}
					// phiExpr
					else if (right instanceof PhiExpr) {
						List<Value> arg=((PhiExpr) right).getValues();
					
						for(Value value:arg){
							varConn.addEdge(left, value);
						}
						//throw new RuntimeException("PhiExpr processing has not implemented");
					} 
					else {
						// 2012.2.11 tao modified
						// throw new RuntimeException();
					}
				}
				// 6. g = r, l.f = r, l[] = r 
				else if (left instanceof ConcreteRef) {
					// Actually, instanceFieldRef may not escape, but tracking the usage of field is
					// costly. We want to keep our escape analysis as fast as possible. Moreover,
					// this way, assuming all field access will escape, reduces the precision of our analysis,
					// but increases the safeness of our analysis.
					// FIXME l.f = r may not escape
					if (right instanceof Local) {
						if (left instanceof InstanceFieldRef) {
							Value base =  ((InstanceFieldRef) left).getBase();
							if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
								varConn.addEdge(base, right);
							} else {
								varConn.addEdge(Boolean.TRUE, right);
							}
						} else if (left instanceof ArrayRef) {
							Value base =  ((ArrayRef) left).getBase();
							if (mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) u)) {
								varConn.addEdge(base, right);
							} else {
								// TODO too conservative
//								varConn.addEdge(Boolean.TRUE, right);
							}
						} else if (left instanceof StaticFieldRef) {
							varConn.addEdge(Boolean.TRUE, right);
						}
					}
				}
				// others
				else {
					throw new RuntimeException();
				}
			} else if (u instanceof InvokeStmt) {
				handleMethodCallForEscape(varConn, null, ((InvokeStmt) u).getInvokeExpr(), u);
			}

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("after visit unit: {}", u);
				breadthFirstTraverse(varConn, null, true);
			}
		}

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("before remove edges: ");
			breadthFirstTraverse(varConn, null, true);
		}
		// solve constraints
		Set escaped = GraphHelper.getReachables(varConn, Boolean.TRUE);
		escapedVars[m.getNumber()] = escaped;
		// escapedMethod is used by callers to check if all return values of callee have escaped.
		// However, some escaped operands in callee, including this object and formal parameters,
		// might not be escaped in caller. We remove all edges of l = @param and l = @this
		for (Pair edge : removeEdges) {
			varConn.removeEdge(edge.getO1(), edge.getO2());
		}
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("after remove edges: {}", varConn);
			breadthFirstTraverse(varConn, null, true);
		}
		Set escapedAfterRemovingEdge = GraphHelper.getReachables(varConn, Boolean.TRUE);
		realEscapedVars[m.getNumber()] = escapedAfterRemovingEdge;
		boolean esc = false;
		for (Local r : returns) {
			if (escapedAfterRemovingEdge.contains(r)) {
				esc = true;
				break;
			}
		}
		escapedMethod[m.getNumber()] = esc;
		// TODO build escape relationship between parameters
	}

	private static <N> Collection<N> breadthFirstTraverse(DirectedGraph<N> graph, N start, boolean forwards) {
		LinkedList<N> workingList = new LinkedList<>();
		Set<N> visited = new HashSet<>();

		if (start == null) {
			if (forwards) {
				workingList.addAll(graph.getHeads());
			} else {
				workingList.addAll(graph.getTails());
			}
		} else {
			workingList.add(start);
		}

		while (!workingList.isEmpty()) {
			N node = workingList.removeFirst();
			if (visited.contains(node)) {
				continue;
			}
			visited.add(node);
			List<N> next;
			if (forwards) {
				next = graph.getSuccsOf(node);
				LOGGER.trace("{} -> {}", node, next);
			} else {
				next = graph.getPredsOf(node);
				LOGGER.trace("{} <- {}", node, next);
			}
			workingList.addAll(next);
		}
		return visited;
	}

	public void build() {
		Date startTime = new Date();

		freshMethod = new boolean[SootUtils.getMethodCount()];
		nonFreshVars = new Set[SootUtils.getMethodCount()];
		returnedVars = new Set[SootUtils.getMethodCount()];
		escapedVars = new Set[SootUtils.getMethodCount()];
		realEscapedVars = new Set[SootUtils.getMethodCount()];
		escapedMethod = new boolean[SootUtils.getMethodCount()];

		// alias analysis
		if (mustAliasQuery == null) {
			mustAliasQuery = new MustAliasIdentityLocalsQuery();
			mustAliasQuery.build();
		}

		List<?> rm = Cache.v().getReverseTopologicalOrder();
		for (Iterator<?> it = rm.iterator(); it.hasNext();) {
			SootMethod sootMethod = (SootMethod) it.next();
			if (sootMethod.isConcrete()) {
				boolean hasRefReturn = sootMethod.getReturnType() instanceof RefLikeType;
				if (hasRefReturn) {
					LOGGER.trace("[LocalityAnalysis] returned analysis for {}: {}",
							sootMethod.getSignature(), sootMethod.getNumber());
					returnedAnalysis(sootMethod);
				} else {
					returnedVars[sootMethod.getNumber()] = Collections.EMPTY_SET;
				}
			}
		}

		for (Iterator<?> it = rm.iterator(); it.hasNext();) {
			SootMethod sootMethod = (SootMethod) it.next();
			if (sootMethod.isConcrete()) {
				LOGGER.trace("[LocalityAnalysis] escape analysis for {}: {}",
						sootMethod.getSignature(), sootMethod.getNumber());
				escapedAnalysis(sootMethod);

				LOGGER.trace("[LocalityAnalysis] fresh analysis for {}: {}",
						sootMethod.getSignature(), sootMethod.getNumber());
				freshAnalysis(sootMethod);
			}
		}

		cg = null;
		Date endTime = new Date();
		LOGGER.info("[LocalityAnalysis] {} methods analyszed in {}", rm.size(), Utils.getTimeConsumed(startTime, endTime));
	}

	public Set<Local> getReturnedLocals(SootMethod m) {
		return returnedVars[m.getNumber()];
	}

	public Set<Local> getEscapedLocals(SootMethod m) {
		return escapedVars[m.getNumber()];
	}

	public Set<Local> getRealEscapedLocals(SootMethod m) {
		return realEscapedVars[m.getNumber()];
	}

	public boolean isReturnValueEscaped(SootMethod m) {
		return escapedMethod[m.getNumber()];
	}

	/**
	 * if variable v in method m is local
	 * @param m method
	 * @param v variable
	 * @return
	 */
	public boolean isRefTgtLocal(SootMethod m, Local v) {
		int mId = m.getNumber();
		Set<Local> escaped = escapedVars[mId];
		Set<Local> returned = returnedVars[mId];
		Set<Local> nonfresh = nonFreshVars[mId];

		// if v escape from m
		if (escaped == null || escaped.contains(v)) {
			return false;
		}
		// if v return from m
		if (returned == null || returned.contains(v)) {
			return false;
		}
		// if v is non-fresh variable
		if (nonfresh.contains(v)) {
			return false;
		}

		return true;
	}

	@Override
	public boolean isRefTgtEscape(SootMethod m, Local v) {
		int mId = m.getNumber();
		if (escapedVars[mId].contains(v)) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean isRefTgtRealEscape(SootMethod m, Local v) {
		int mId = m.getNumber();
		if (realEscapedVars[mId].contains(v)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * get all local variables in method
	 * @param m
	 * @return
	 */
	public Set<Local> getLocalityLocals(SootMethod m) {
		Set<Local> localVars = new HashSet<>();
		for (Local l : m.getActiveBody().getLocals()) {
			if (isRefTgtLocal(m, l)) {
				localVars.add(l);
			}
		}
		return localVars;
	}
}
