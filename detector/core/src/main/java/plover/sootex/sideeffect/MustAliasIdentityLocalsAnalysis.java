package plover.sootex.sideeffect;

import plover.sootex.location.AccessPath;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.pointer.LocalMustAliasAnalysis;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;

/**
 * MustAliasIdentityLocalAnalysis attempts to determine if a local variables must point to the identity locals,
 * including this local and parameter locals.
 *
 * The underlying abstraction is based on global value numbering. And the implementation is inspired by
 * {@link soot.jimple.toolkits.pointer.LocalMustAliasAnalysis}.
 *
 * See also {@link soot.jimple.toolkits.pointer.LocalMustAliasAnalysis} for original version.
 *
 *
 * @see soot.jimple.toolkits.pointer.LocalMustAliasAnalysis
 */

public class MustAliasIdentityLocalsAnalysis extends ForwardFlowAnalysis<Unit, HashMap<Value, Fact>> {

    /**
     * The set of all local variables and field references that we track. This set contains objects of type {@link Local} and,
     * if tryTrackFieldAssignments is enabled, it may also contain {@link EquivalentValue}s of {@link FieldRef}s. If so, these
     * field references are to be tracked on the same way as {@link Local}s are.
     */
    protected Set<Value> localsAndFieldRefs;

    /** maps from right-hand side expressions (non-locals) to value numbers */
    protected transient Map<Value, Integer> rhsToNumber;

    /** maps from a merge point (a unit) and a value to the unique value number of that value at this point */
    protected transient Map<Unit, Map<Value, Fact>> mergePointToValueToNumber;

    /** the next value number */
    protected int nextNumber = 1;

    /** the containing method */
    protected SootMethod container;

    /**
     * Creates a new {@link LocalMustAliasAnalysis} tracking local variables.
     */
    public MustAliasIdentityLocalsAnalysis(UnitGraph g) {
        this(g, false);
    }

    /**
     * Creates a new {@link LocalMustAliasAnalysis}. If tryTrackFieldAssignments, we run an interprocedural side-effects
     * analysis to determine which fields are (transitively) written to by this method. All fields which that are not written
     * to are tracked just as local variables. This semantics is sound for single-threaded programs.
     */
    public MustAliasIdentityLocalsAnalysis(UnitGraph g, boolean tryTrackFieldAssignments) {
        super(g);
        this.container = g.getBody().getMethod();
        this.localsAndFieldRefs = new HashSet<Value>();

        // add all locals
        for (Local l : (Collection<Local>) g.getBody().getLocals()) {
            if (l.getType() instanceof RefLikeType) {
                this.localsAndFieldRefs.add(l);
            }
        }

        if (tryTrackFieldAssignments) {
            this.localsAndFieldRefs.addAll(trackableFields());
        }

        this.rhsToNumber = new HashMap<Value, Integer>();
        this.mergePointToValueToNumber = new HashMap<Unit, Map<Value, Fact>>();

        doAnalysis();

        // not needed any more
        this.rhsToNumber = null;
        this.mergePointToValueToNumber = null;
    }

    /**
     * Computes the set of {@link EquivalentValue}s of all field references that are used in this method but not set by the
     * method or any method transitively called by this method.
     */
    private Set<Value> trackableFields() {
        Set<Value> usedFieldRefs = new HashSet<Value>();
        // add all field references that are in use boxes
        for (Unit unit : this.graph) {
            Stmt s = (Stmt) unit;
            List<ValueBox> useBoxes = s.getUseBoxes();
            for (ValueBox useBox : useBoxes) {
                Value val = useBox.getValue();
                if (val instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) val;
                    if (fieldRef.getType() instanceof RefLikeType) {
                        usedFieldRefs.add(new EquivalentValue(fieldRef));
                    }
                }
            }
        }

        // prune all fields that are written to
        if (!usedFieldRefs.isEmpty()) {

            if (!Scene.v().hasCallGraph()) {
                throw new IllegalStateException("No call graph found!");
            }

            CallGraph cg = Scene.v().getCallGraph();
            ReachableMethods reachableMethods
                    = new ReachableMethods(cg, Collections.<MethodOrMethodContext>singletonList(container));
            reachableMethods.update();
            for (Iterator<MethodOrMethodContext> iterator = reachableMethods.listener(); iterator.hasNext();) {
                SootMethod m = (SootMethod) iterator.next();
                if (m.hasActiveBody() &&
                        // exclude static initializer of same class (assume that it has already been executed)
                        !(m.getName().equals(SootMethod.staticInitializerName)
                                && m.getDeclaringClass().equals(container.getDeclaringClass()))) {
                    for (Unit u : m.getActiveBody().getUnits()) {
                        List<ValueBox> defBoxes = u.getDefBoxes();
                        for (ValueBox defBox : defBoxes) {
                            Value value = defBox.getValue();
                            if (value instanceof FieldRef) {
                                usedFieldRefs.remove(new EquivalentValue(value));
                            }
                        }
                    }
                }
            }
        }

        return usedFieldRefs;
    }

    @Override
    protected void flowThrough(HashMap<Value, Fact> in, Unit u, HashMap<Value, Fact> out) {
        Stmt s = (Stmt) u;
        out.clear();
        out.putAll(in);

        if (s instanceof DefinitionStmt) {
            DefinitionStmt ds = (DefinitionStmt) s;
            Value lhs = ds.getLeftOp();
            Value rhs = ds.getRightOp();

            if (rhs instanceof CastExpr) {
                // un-box casted value
                CastExpr castExpr = (CastExpr) rhs;
                rhs = castExpr.getOp();
            }

            if ((lhs instanceof Local || (lhs instanceof FieldRef && this.localsAndFieldRefs.contains(new EquivalentValue(lhs))))
                    && lhs.getType() instanceof RefLikeType) {
                if (rhs instanceof Local) {
                    // local-assignment - must be aliased...
                    Fact fact = in.get(rhs);
                    if (fact != null) {
                        out.put(lhs, fact);
                    }
                } else if (rhs instanceof ThisRef || rhs instanceof ParameterRef) {
                    Fact fact = new Fact();
                    // ThisRef can never change; assign unique number
                    fact.setNumber(thisAndParameterRefNumber());
                    // create access path
                    Set<AccessPath> aps = new HashSet<>();
                    aps.add(AccessPath.valueToAccessPath(container, u, lhs));
                    fact.setAliasAPs(aps);
                    out.put(lhs, fact);
                } else if (rhs instanceof InstanceFieldRef) {
                    Fact fact = new Fact();
                    Fact baseFact = in.get(((InstanceFieldRef) rhs).getBase());
                    // baseFact maybe null for locals in catch block and final block
                    // it is because each catch exception is the header of BriefUnitGraph,
                    // so some locals in catch blocks and final blocks are visited before they are defined in normal block
                    // TODO use ExceptionalUnitGraph instead of BriefUnitGraph,
                    //  we need to care about if the exceptional flow will influence our algorithm for side-effect analysis and def-use analysis
                    if (baseFact != null) {
                        // set number with the base object
                        fact.setNumber(baseFact.getNumber());
                        // append field access to access path
                        Set<AccessPath> aps = new HashSet<>();
                        for (AccessPath ap : baseFact.getAliasAPs()) {
                            // handle the case like a.b.b.b, otherwise the analysis will not reach fixed point
                            // if the FieldRef is appended already, we will not append the FieldRef again
                            // a real case can be found in <java.util.TreeMap: java.util.TreeMap$Entry getFirstEntry()>
                            // a more complex real case can be found in <java.util.TreeMap: java.lang.Object put(java.lang.Object,java.lang.Object)>
                            // TODO we should improve the implementation of {@link AccessPath} to support subField like
                            //  what FlowDroid does
                            SootField field = ((InstanceFieldRef) rhs).getField();
                            Object[] accessors = ap.getAccessors();
                            boolean appended = false;
                            for (Object accessor : accessors) {
                                if (field.equals(accessor)) {
                                    appended = true;
                                    break;
                                }
                            }
                            if (!appended) {
                                aps.add(ap.appendFieldRef(field));
                            }
                        }
                        fact.setAliasAPs(aps);
                        out.put(lhs, fact);
                    }
                } else if (rhs instanceof ArrayRef) {
                    Fact fact = new Fact();
                    Fact baseFact = in.get(((ArrayRef) rhs).getBase());
                    // baseFact maybe null for locals in catch block and final block
                    // it is because each catch exception is the header of BriefUnitGraph,
                    // so some locals in catch blocks and final blocks are visited before they are defined in normal block
                    // TODO use ExceptionalUnitGraph instead of BriefUnitGraph,
                    //  we need to care about if the exceptional flow will influence our algorithm for side-effect analysis and def-use analysis
                    if (baseFact != null) {
                        // set number with the base object
                        fact.setNumber(baseFact.getNumber());
                        // append array reference to access path
                        Set<AccessPath> aps = new HashSet<>();
                        for (AccessPath ap : baseFact.getAliasAPs()) {
                            // handle the case like a[i1][i2][i3], otherwise the analysis will not reach fixed point
                            // we stop to append ArrayRef when there are two ArrayRef appended already
                            // a real case can be found in <sun.util.PreHashedMap: java.lang.Object get(java.lang.Object)>
                            // TODO we should improve the implementation of {@link AccessPath} to support subField like
                            //  what FlowDroid does
                            Object[] accessors = ap.getAccessors();
                            boolean appended = false;
                            for (Object accessor : accessors) {
                                if (AccessPath.getArrayRef().equals(accessor)) {
                                    appended = true;
                                    break;
                                }
                            }
                            if (!appended) {
                                aps.add(ap.appendArrayRef());
                            }
                        }
                        fact.setAliasAPs(aps);
                        out.put(lhs, fact);
                    }
                } else {
                    // TODO handle getter methods
                    // assign number for expression
                    out.put(lhs, new Fact(numberOfRhs(rhs), new HashSet<>()));
                }
            }
        } else {
            // which other kind of statement has def-boxes? hopefully none...
            assert s.getDefBoxes().isEmpty();
        }
    }

    private Integer numberOfRhs(Value rhs) {
        EquivalentValue equivValue = new EquivalentValue(rhs);
        if (localsAndFieldRefs.contains(equivValue)) {
            rhs = equivValue;
        }
        Integer num = rhsToNumber.get(rhs);
        if (num == null) {
            num = nextNumber++;
            rhsToNumber.put(rhs, num);
        }
        return num;
    }

    public static int thisAndParameterRefNumber() {
        // unique number for ThisRef and ParameterRef
        return -1;
    }

    /** Initial most conservative value: We leave it away to save memory, implicitly UNKNOWN. */
    @Override
    protected HashMap<Value, Fact> entryInitialFlow() {
        return new HashMap<Value, Fact>();
    }

    /** Initial bottom value: objects have no definitions. */
    @Override
    protected HashMap<Value, Fact> newInitialFlow() {
        return new HashMap<Value, Fact>();
    }

    @Override
    protected void merge(Unit succUnit, HashMap<Value, Fact> inMap1, HashMap<Value, Fact> inMap2,
                         HashMap<Value, Fact> outMap) {
        for (Value l : localsAndFieldRefs) {
            Fact f1 = inMap1.get(l), f2 = inMap2.get(l);
            if (f1 == null) {
                outMap.put(l, f2);
            } else if (f2 == null) {
                outMap.put(l, f1);
            } else if (f1.getNumber().equals(f2.getNumber())) {
                Set<AccessPath> aps = new HashSet<>();
                aps.addAll(f1.getAliasAPs());
                aps.addAll(f2.getAliasAPs());
                outMap.put(l, new Fact(f1.getNumber(), aps));
            } else {
                /*
                 * Merging two different values is tricky... A naive approach would be to assign UNKNOWN. However, that would lead to
                 * imprecision in the following case:
                 *
                 * x = null; if(p) x = new X(); y = x; z = x;
                 *
                 * Even though it is obvious that after this block y and z are aliased, both would be UNKNOWN :-( Hence, when merging
                 * the numbers for the two branches (null, new X()), we assign a value number that is unique to that merge location.
                 * Consequently, both y and z is assigned that same number! In the following it is important that we use an
                 * IdentityHashSet because we want the number to be unique to the location. Using a normal HashSet would make it
                 * unique to the contents. (Eric)
                 */

                // retrieve the unique number for l at the merge point succUnit
                // if there is no such number yet, generate one
                // then assign the number to l in the outMap
                Map<Value, Fact> valueToNumber = mergePointToValueToNumber.get(succUnit);
                Integer number = null;
                if (valueToNumber == null) {
                    valueToNumber = new HashMap<Value, Fact>();
                    mergePointToValueToNumber.put(succUnit, valueToNumber);
                } else if (valueToNumber.get(l) != null){
                    number = valueToNumber.get(l).getNumber();
                }
                if (number == null) {
                    number = nextNumber++;
                    // use null or empty set to initialize  fact?
                    valueToNumber.put(l, new Fact(number, new HashSet<>()));
                }
                outMap.put(l, new Fact(number, new HashSet<>()));
            }
        }
    }

    @Override
    protected void merge(HashMap<Value, Fact> in1, HashMap<Value, Fact> in2, HashMap<Value, Fact> out) {
        // Copy over in1. This will be the baseline
        out.putAll(in1);

        // Merge in in2. Make sure that we do not have ambiguous values.
        for (Value val : in2.keySet()) {
            Integer i1 = in1.get(val).getNumber();
            Integer i2 = in2.get(val).getNumber();
            if (i2.equals(i1)) {
                out.put(val, in2.get(val));
            } else {
                throw new RuntimeException("Merge of different IDs not supported");
            }
        }
    }

    @Override
    protected void copy(HashMap<Value, Fact> sourceMap, HashMap<Value, Fact> destMap) {
        destMap.clear();
        destMap.putAll(sourceMap);
    }

    public boolean isMustAliasToIdentityLocals(Value v, Unit u) {
        Fact fact = getFlowBefore(u).get(v);
        // fact maybe null for locals in catch block and final block
        // it is because each catch exception is the header of BriefUnitGraph,
        // so some locals in catch blocks and final blocks are visited before they are defined in normal block
        // TODO use ExceptionalUnitGraph instead of BriefUnitGraph,
        //  we need to care about if the exceptional flow will influence our algorithm for side-effect analysis and def-use analysis
        if (fact == null || !fact.getNumber().equals(thisAndParameterRefNumber())) {
            return false;
        }
        return true;
    }

    public Set<AccessPath> getMustAliasToIdentityLocals(Value v, Unit u) {
        Fact fact = getFlowBefore(u).get(v);
        // fact maybe null for locals in catch block and final block
        // it is because each catch exception is the header of BriefUnitGraph,
        // so some locals in catch blocks and final blocks are visited before they are defined in normal block
        // TODO use ExceptionalUnitGraph instead of BriefUnitGraph,
        //  we need to care about if the exceptional flow will influence our algorithm for side-effect analysis and def-use analysis
        if (fact == null || !fact.getNumber().equals(thisAndParameterRefNumber())) {
            return new HashSet<>();
        } else {
            return fact.getAliasAPs();
        }
    }

    public boolean isMustAliasToIdentityLocalAtExit(Value v) {
        for (Unit u : graph.getTails()) {
            Fact fact = getFlowBefore(u).get(v);
            if (!fact.getNumber().equals(thisAndParameterRefNumber())) {
                return false;
            }
        }
        return true;
    }

    public Set<AccessPath> getMustAliasToIdentityLocalAtExit(Value v) {
        Set<AccessPath> aps = new HashSet<>();
        for (Unit u : graph.getTails()) {
            Fact fact = getFlowBefore(u).get(v);
            if (!fact.getNumber().equals(thisAndParameterRefNumber())) {
                return new HashSet<>();
            } else {
                aps.addAll(fact.getAliasAPs());
            }
        }
        return aps;
    }

}


class Fact {
    private Integer number;
    private Set<AccessPath> aliasAPs;

    public Fact() {
    }

    public Fact(Integer number, Set<AccessPath> aliasAPs) {
        this.number = number;
        this.aliasAPs = aliasAPs;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public Set<AccessPath> getAliasAPs() {
        return aliasAPs;
    }

    public void setAliasAPs(Set<AccessPath> aliasAPs) {
        this.aliasAPs = aliasAPs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fact fact = (Fact) o;
        return number.equals(fact.number) &&
                aliasAPs.equals(fact.aliasAPs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, aliasAPs);
    }

    @Override
    public String toString() {
        return "Fact{" +
                "number=" + number +
                ", aliasAPs=" + aliasAPs +
                '}';
    }
}