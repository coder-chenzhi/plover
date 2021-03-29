package plover.guards;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2004 Jennifer Lhotak
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.*;

import com.google.common.collect.Ordering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.Unit;
import soot.jimple.Stmt;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.UnitGraph;

public class LoopFinder{

    public static final Logger LOGGER = LoggerFactory.getLogger(LoopFinder.class);

    public static Set<Loop> getLoops(Body b) {
        return getLoops(new ExceptionalUnitGraph(b));
    }

    public static Set<Loop> getLoops(UnitGraph g) {
        DominatorsFinder<Unit> a = new MHGDominatorsFinder<>(g);
        Map<Stmt, List<Stmt>> loops = new HashMap<Stmt, List<Stmt>>();

        for (Unit u : g.getBody().getUnits()) {
            List<Unit> succs = g.getSuccsOf(u);
            if (succs == null) {
                continue;
            }
            List<Unit> dominaters = a.getDominators(u);
            List<Stmt> headers = new ArrayList<Stmt>();

            for (Unit succ : succs) {
                if (dominaters.contains(succ)) {
                    // header succeeds and dominates s, we have a loop
                    headers.add((Stmt) succ);
                }
            }

            for (Unit header : headers) {
                List<Stmt> loopBody = getLoopBodyFor(header, u, g);
                if (loops.containsKey(header)) {
                    // merge bodies
                    List<Stmt> lb1 = loops.get(header);
                    loops.put((Stmt) header, union(lb1, loopBody));
                } else {
                    loops.put((Stmt) header, loopBody);
                }
            }
        }

        Set<Loop> ret = new HashSet<Loop>();
        for (Map.Entry<Stmt, List<Stmt>> entry : loops.entrySet()) {
            try {
                Loop loop = new Loop(entry.getKey(), entry.getValue(), g);
                ret.add(loop);
            } catch (AssertionError error) {
                LOGGER.warn("Get Loop Error!");
            }
        }

        return ret;
    }

    private static List<Stmt> getLoopBodyFor(Unit header, Unit node, UnitGraph g) {
        List<Stmt> loopBody = new ArrayList<Stmt>();
        Deque<Unit> stack = new ArrayDeque<Unit>();

        loopBody.add((Stmt) header);
        stack.push(node);

        while (!stack.isEmpty()) {
            Stmt next = (Stmt) stack.pop();
            if (!loopBody.contains(next)) {
                // add next to loop body
                loopBody.add(0, next);
                List<Unit> preds = g.getPredsOf(next);
                // put all preds of next on stack
                for (Unit u : preds) {
                    stack.push(u);
                }
            }
        }

        if ((node != header || loopBody.size() != 1) && loopBody.get(loopBody.size() - 2) != node)
            throw new AssertionError();
        if (loopBody.get(loopBody.size() - 1) != header) throw new AssertionError();

        // sort loop body by original lexical order
        // the result of Body.getUnits() maintains the original lexical order
        List<Unit> originalOrder = new ArrayList<>(g.getBody().getUnits());
        Collections.sort(loopBody, Ordering.explicit(originalOrder));
        return loopBody;
    }

    private static List<Stmt> union(List<Stmt> l1, List<Stmt> l2) {
        for (Stmt next : l2) {
            if (!l1.contains(next)) {
                l1.add(next);
            }
        }
        return l1;
    }

    private static boolean isSuccessor(Unit s1, Unit s2, UnitGraph g) {
        Deque<Unit> stack = new ArrayDeque<Unit>(g.getPredsOf(s1));
        while (!stack.isEmpty()) {
            Unit next = stack.pop();
            if (s2.equals(next)) {
                return true;
            } else {
                for (Unit u : g.getPredsOf(next)) {
                    stack.push(u);
                }
            }
        }
        return false;
    }

}
