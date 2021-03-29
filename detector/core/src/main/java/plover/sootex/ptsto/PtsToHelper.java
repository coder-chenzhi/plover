package plover.sootex.ptsto;

import java.util.*;

import plover.soot.CollectionUtils;
import plover.sootex.location.*;
import soot.ArrayType;
import soot.Immediate;
import soot.RefLikeType;
import soot.RefType;
import soot.SootField;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;


@SuppressWarnings({ "rawtypes", "unchecked" })
public class PtsToHelper {
	public static IPtsToQuery createPointsToQuery(PointsToAnalysisType type) {
		IPtsToQuery ptsto = null;
		switch (type) {
		case SPARK:
			ptsto = new SparkPtsToQuery();
			break;
		case TYPE_BASED:
			ptsto = new TypeBasedPtsToQuery(false);
			break;
		case NAIVE:
			ptsto = new NaivePtsToQuery();
			break;
		default:
			throw new RuntimeException("pointer analysis - " + type + " - unsupported.");
		}

		return ptsto;
	}
	   
	static Set<Location> getField(Set<InstanceObject> objects, SootField field) {
		Set<Location> hset = new HashSet<Location>();
		for (InstanceObject o: objects) {			 
			if (!(o instanceof CommonInstObject))
				continue;

			CommonInstObject hobj = (CommonInstObject)o;
			HeapField f = hobj.getField(field);

			// Heap field can be null, if the object is considered as an atomic type
			if (f != null)
				hset.add(f);
		}
		return hset;
	}

	static Set<Location> getArrayElement(Set<InstanceObject> objects) {
		Set<Location> hset = new HashSet<Location>();
		for (InstanceObject o: objects) {			 
			if (!(o instanceof ArraySpace))
				continue;

			ArraySpace hobj = (ArraySpace) o;
			Location p = hobj.getElement();
			hset.add(p);
		}
		return hset;
	}
	
	public static boolean mayAlias(IPtsToQuery ptsto, Location ptr1, Location ptr2){    
		if(ptr1==ptr2){
    		return true;
    	}
		
    	//firstly check the type
    	Type t1 = ptr1.getType();
    	Type t2 = ptr2.getType();
    	
    	if(!(t1 instanceof RefLikeType) || !(t2 instanceof RefLikeType)){
    		return false;
    	}
    	else if(t1 instanceof ArrayType && !(t2 instanceof ArrayType)){
    		return false;
    	}
    	else if(t1 instanceof RefType && !(t2 instanceof RefType)){
    		return false;
    	}
    	
    	//check points-to sets
    	Set pt1 = ptsto.getPointTos(null, null, ptr1);
		Set pt2 = ptsto.getPointTos(null, null, ptr2);		
		return CollectionUtils.hasInterset(pt1, pt2);
    } 

	/**
	 * Get all abstract locations that may alias with 'ap' in 'stmt'
	 * @param stmt
	 * @param ap
	 * @param query
	 * @return
	 */
	private static Set<Location> getAliasedLocations(Unit stmt, AccessPath ap, IPtsToQuery query){
        Set cur = new HashSet(40);
        cur.add(ap.getRoot()); 
        
        for(Object ac: ap.getAccessors()){
        	Set<InstanceObject> heaps;
        	if(cur.size() == 1){
        		heaps = query.getPointTos(null, stmt, (Location)cur.iterator().next());
        	} else {
        		heaps = new HashSet<InstanceObject>(100);
        		for(Object p: cur){      
        	        Set s = query.getPointTos(null, stmt,(Location)p);
        	        heaps.addAll(s);
        	    }
        	}    
        
            if(ac instanceof SootField){
                cur = getField(heaps, (SootField)ac);
            }else{
                cur = getArrayElement(heaps);
            }            
        }
        return cur;
    }
 
	//TODO performance
	public static Set<Location> getAccessedLocations(IPtsToQuery ptsto, Unit stmt, AccessPath ap){
		// field-sensitive
//		Set<Location> locs = PtsToHelper.getAliasedLocations(null, ap, ptsto);
		// field-insensitive point-to analysis
		Set<Location> locs = new HashSet<Location>(Collections.singletonList(ap.getRoot()));
		return locs;

	}
    
	protected static final Set<Location> getAliasedLocations(Unit stmt, Value ref, IPtsToQuery query){
		if(ref instanceof InstanceFieldRef){
			InstanceFieldRef iref = (InstanceFieldRef)ref;
			Location base = Location.valueToLocation(iref.getBase());
			Set<InstanceObject> heaps = query.getPointTos(null, stmt,base);			
			Set out = getField(heaps, iref.getField());
			return out;
		}
		else if(ref instanceof ArrayRef){
			ArrayRef aref = (ArrayRef)ref;
			Location base = Location.valueToLocation(aref.getBase());
			Set<InstanceObject> heaps = query.getPointTos(null, stmt,base);
			Set out = getArrayElement(heaps);
			return out;
		}
		else if(ref instanceof Immediate){
			Location base = Location.valueToLocation(ref);
			Set out = new HashSet(1);
			out.add(base);
			return out;
		}
		else{
			throw new RuntimeException();
		}
    }
}
