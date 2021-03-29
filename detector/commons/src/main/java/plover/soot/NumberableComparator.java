package plover.soot;

import soot.util.Numberable;

import java.util.Comparator;

/**
 *
 */
public class NumberableComparator implements Comparator<Numberable> {
	protected NumberableComparator(){}
	private static NumberableComparator _instance = new NumberableComparator();
	
	public static NumberableComparator v(){
		return _instance;
	}
	
	public int compare(Numberable n1, Numberable n2) {
		return n1.getNumber()-n2.getNumber();
	}
}
